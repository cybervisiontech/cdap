/*
 * Copyright 2014 Continuuity,Inc. All Rights Reserved.
 */
package com.continuuity.data2.transaction.stream;

import com.continuuity.api.common.Bytes;
import com.continuuity.api.flow.flowlet.StreamEvent;
import com.continuuity.common.queue.QueueName;
import com.continuuity.data.file.FileReader;
import com.continuuity.data.stream.ForwardingStreamEvent;
import com.continuuity.data.stream.StreamEventOffset;
import com.continuuity.data.stream.StreamFileOffset;
import com.continuuity.data.stream.StreamUtils;
import com.continuuity.data2.queue.ConsumerConfig;
import com.continuuity.data2.queue.DequeueResult;
import com.continuuity.data2.queue.DequeueStrategy;
import com.continuuity.data2.transaction.Transaction;
import com.continuuity.data2.transaction.queue.ConsumerEntryState;
import com.continuuity.data2.transaction.queue.QueueEntryRow;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A {@link StreamConsumer} that read events from stream file and uses a table to store consumer states.
 *
 * <p>
 * State table ROW key schema:
 *
 * <pre>{@code
 *   row_key = <group_id> <stream_file_offset>
 *   group_id = 8 bytes consumer group id
 *   stream_file_offset = <partition_start> <partition_end> <name_prefix> <sequence_id> <offset>
 *   partition_start = 8 bytes timestamp of partition start time
 *   partition_end = 8 bytes timestamp of partition end time
 *   name_prefix = DataOutput UTF-8 output.
 *   sequence_id = 4 bytes stream file sequence id
 *   offset = 8 bytes offset inside the stream file
 * }</pre>
 *
 * The state table has single column
 * ({@link QueueEntryRow#COLUMN_FAMILY}:{@link QueueEntryRow#STATE_COLUMN_PREFIX}) to store state.
 *
 * The state value:
 *
 * <pre>{@code
 *   state_value = <write_pointer> <instance_id> <state>
 *   write_pointer = 8 bytes Transaction write point of the consumer who update this state.
 *   instance_id = 4 bytes Instance id of the consumer who update this state.
 *   state = ConsumerEntryState.getState(), either CLAIMED or PROCESSED
 * }</pre>
 *
 */
@NotThreadSafe
public abstract class AbstractStreamFileConsumer implements StreamConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractStreamFileConsumer.class);
  private static final HashFunction ROUND_ROBIN_HASHER = Hashing.murmur3_32();

  // Persist state at most once per second.
  private static final long STATE_PERSIST_MIN_INTERVAL = TimeUnit.SECONDS.toNanos(1);

  private static final DequeueResult<StreamEvent> EMPTY_RESULT = DequeueResult.Empty.result();
  private static final Function<PollStreamEvent, byte[]> EVENT_ROW_KEY = new Function<PollStreamEvent, byte[]>() {
    @Override
    public byte[] apply(PollStreamEvent input) {
      return input.getStateRow();
    }
  };
  private static final Function<PollStreamEvent, StreamEvent> CONVERT_STREAM_EVENT =
    new Function<PollStreamEvent, StreamEvent>() {
      @Override
      public StreamEvent apply(PollStreamEvent input) {
        return input;
      }
    };

  protected final byte[] stateColumnName;

  private final QueueName streamName;
  private final StreamConfig streamConfig;
  private final ConsumerConfig consumerConfig;
  private final StreamConsumerStateStore consumerStateStore;
  private final FileReader<StreamEventOffset, Iterable<StreamFileOffset>> reader;

  // Records rows that shouldn't be processed by this consumer.
  // The rows are only needed for entries that are already in the state table when this consumer start,
  // since the consumer claim entry logic already handle different strategies.
  private final SortedSet<byte[]> ignoreRows;
  private final SortedSet<byte[]> initScanned;

  private final StreamConsumerState consumerState;
  private final List<StreamEventOffset> eventCache;
  private final byte[] stateContent;
  private Transaction transaction;
  private List<PollStreamEvent> polledEvents;
  private long nextPersistStateTime;
  private boolean closed;
  private StreamConsumerState lastPersistedState;

  /**
   * @param streamConfig Stream configuration.
   * @param consumerConfig Consumer configuration.
   * @param reader For reading stream events. This class is responsible for closing the reader.
   */
  protected AbstractStreamFileConsumer(StreamConfig streamConfig, ConsumerConfig consumerConfig,
                                       FileReader<StreamEventOffset, Iterable<StreamFileOffset>> reader,
                                       StreamConsumerStateStore consumerStateStore) {

    System.out.println("Consumer: " + consumerConfig);
    this.streamName = QueueName.fromStream(streamConfig.getName());
    this.streamConfig = streamConfig;
    this.consumerConfig = consumerConfig;
    this.consumerStateStore = consumerStateStore;
    this.reader = reader;

    this.ignoreRows = Sets.newTreeSet(Bytes.BYTES_COMPARATOR);
    this.initScanned = Sets.newTreeSet(new Comparator<byte[]>() {
      @Override
      public int compare(byte[] bytes1, byte[] bytes2) {
        // Compare row keys without the offset part (last 8 bytes).
        return Bytes.compareTo(bytes1, 0, bytes1.length - Longs.BYTES, bytes2, 0, bytes2.length - Longs.BYTES);
      }
    });

    this.eventCache = Lists.newArrayList();
    this.consumerState = new StreamConsumerState(consumerConfig.getGroupId(), consumerConfig.getInstanceId());

    this.stateContent = new byte[Longs.BYTES + Ints.BYTES + 1];
    this.stateColumnName = Bytes.add(QueueEntryRow.STATE_COLUMN_PREFIX, Bytes.toBytes(consumerConfig.getGroupId()));
  }

  protected void doClose() throws IOException {
    // No-op.
  }

  protected abstract boolean claimFifoEntry(byte[] row, byte[] value) throws IOException;

  protected abstract void updateState(Iterable<byte[]> rows, int size, byte[] value) throws IOException;

  protected abstract void undoState(Iterable<byte[]> rows, int size) throws IOException;

  protected abstract StateScanner scanStates(byte[] startRow, byte[] endRow) throws IOException;

  @Override
  public final QueueName getStreamName() {
    return streamName;
  }

  @Override
  public final ConsumerConfig getConsumerConfig() {
    return consumerConfig;
  }

  @Override
  public final DequeueResult<StreamEvent> poll(int maxEvents, long timeout,
                                               TimeUnit timeoutUnit) throws IOException, InterruptedException {

    // Number of events it tries to read by multiply the maxEvents with the group size. It doesn't have to be exact,
    // just a rough estimate for better read throughput.
    // Also, this maxRead is used throughout the read loop below, hence some extra events might be read and cached
    // for next poll call.
    int maxRead = maxEvents * consumerConfig.getGroupSize();

    // Try to read from cache if any
    if (!eventCache.isEmpty()) {
      getEvents(eventCache, polledEvents, maxEvents);
    }

    if (polledEvents.size() == maxEvents) {
      return new SimpleDequeueResult(polledEvents);
    }

    long timeoutNano = timeoutUnit.toNanos(timeout);
    Stopwatch stopwatch = new Stopwatch();
    stopwatch.start();

    // Read from the underlying file reader
    snapshotFileOffsets();
    while (timeoutNano >= 0 && polledEvents.size() < maxEvents) {
      int readCount = reader.read(eventCache, maxRead, timeoutNano, TimeUnit.NANOSECONDS);
      timeoutNano -= stopwatch.elapsedTime(TimeUnit.NANOSECONDS);

      if (readCount > 0) {
        getEvents(eventCache, polledEvents, polledEvents.size() - maxEvents);
      }
    }

    if (polledEvents.isEmpty()) {
      return EMPTY_RESULT;
    } else {
      return new SimpleDequeueResult(polledEvents);
    }
  }

  private void snapshotFileOffsets() {
    List<StreamFileOffset> offsets = Lists.newArrayList();
    for (StreamFileOffset offset : reader.getPosition()) {
      offsets.add(new StreamFileOffset(offset));
    }
    consumerState.setState(offsets);
  }

  @Override
  public final void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    try {
      persistConsumerState();
      doClose();
    } finally {
      try {
        reader.close();
      } finally {
        consumerStateStore.close();
      }
    }
  }

  @Override
  public final void startTx(Transaction tx) {
    transaction = tx;
    if (consumerConfig.getDequeueStrategy() == DequeueStrategy.FIFO && consumerConfig.getGroupSize() > 1) {
      // Pre-fill the state value.
      encodeStateColumn(ConsumerEntryState.CLAIMED, stateContent);
    }

    if (polledEvents == null) {
      polledEvents = Lists.newArrayList();
    } else {
      polledEvents.clear();
    }
  }

  @Override
  public final Collection<byte[]> getTxChanges() {
    // Guaranteed no conflict in the consumer logic
    return ImmutableList.of();
  }

  @Override
  public final boolean commitTx() throws Exception {
    if (polledEvents.isEmpty()) {
      return true;
    }

    // For each polled events, set the state column to PROCESSED
    updateState(Iterables.transform(polledEvents, EVENT_ROW_KEY),
                polledEvents.size(),
                encodeStateColumn(ConsumerEntryState.PROCESSED, stateContent));

    return true;
  }

  @Override
  public void postTxCommit() {
    long currentNano = System.nanoTime();
    if (currentNano >= nextPersistStateTime) {
      nextPersistStateTime = currentNano + STATE_PERSIST_MIN_INTERVAL;
      persistConsumerState();
    }
  }

  @Override
  public boolean rollbackTx() throws Exception {
    if (polledEvents.isEmpty()) {
      return true;
    }
    undoState(Iterables.transform(polledEvents, EVENT_ROW_KEY),
              polledEvents.size());
    return true;
  }

  @Override
  public String getName() {
    return toString();
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
      .add("stream", streamConfig)
      .add("consumer", consumerConfig)
      .toString();
  }

  private void getEvents(List<? extends StreamEventOffset> source,
                         List<? super PollStreamEvent> result,
                         int maxEvents) throws IOException {
    Iterator<? extends StreamEventOffset> iterator = Iterators.consumingIterator(source.iterator());
    while (result.size() < maxEvents && iterator.hasNext()) {
      StreamEventOffset event = iterator.next();
      byte[] stateRow = claimEntry(event.getOffset(), stateContent);
      if (stateRow == null) {
        continue;
      }
      result.add(new PollStreamEvent(event, stateRow));
    }
  }

  private void persistConsumerState() {
    try {
      if (lastPersistedState == null || !consumerState.equals(lastPersistedState)) {
        consumerStateStore.save(consumerState);
        lastPersistedState = new StreamConsumerState(consumerState);
      }
    } catch (IOException e) {
      LOG.error("Failed to persist consumer state for consumer {} of stream {}", consumerConfig, getStreamName(), e);
    }
  }

  /**
   * Encodes the value for the state column with the current transaction and consumer information.
   *
   * @param state The state to encode
   * @param stateContent byte array to carry the result
   * @return The stateContent byte array
   */
  // TODO: This method is copied from AbstractQueue2Consumer. Future effort is needed to unify them.
  private byte[] encodeStateColumn(ConsumerEntryState state, byte[] stateContent) {
    // State column content is encoded as (writePointer) + (instanceId) + (state)
    Bytes.putLong(stateContent, 0, transaction.getWritePointer());
    Bytes.putInt(stateContent, Longs.BYTES, consumerConfig.getInstanceId());
    Bytes.putByte(stateContent, Longs.BYTES + Ints.BYTES, state.getState());

    return stateContent;
  }

  /**
   * Try to claim a stream event offset.
   *
   * @return The row key for writing to the state table if successfully claimed or {@code null} if not claimed.
   */
  private byte[] claimEntry(StreamFileOffset offset, byte[] claimedStateContent) throws IOException {
    ByteArrayDataOutput out = ByteStreams.newDataOutput(50);
    out.writeLong(consumerConfig.getGroupId());
    StreamUtils.encodeOffset(out, offset);
    byte[] row = out.toByteArray();

    initScanIfNeeded(row);

    // See if the row is in ignore list
    if (ignoreRows.contains(row)) {
      return null;
    }

    if (consumerConfig.getDequeueStrategy() == DequeueStrategy.FIFO) {
      if (consumerConfig.getGroupSize() == 1) {
        // Special FIFO case, no need to write claim entry
        return row;
      }
      return claimFifoEntry(row, claimedStateContent) ? row : null;
    } else {
      // For RoundRobin and Hash partition, the claim is done by matching hashCode to instance id.
      // For Hash, to preserve existing behavior, everything route to instance 0.
      int hashValue = Math.abs(consumerConfig.getDequeueStrategy() == DequeueStrategy.HASH
                        ? 0 : ROUND_ROBIN_HASHER.hashLong(offset.getOffset()).hashCode());
      return consumerConfig.getInstanceId() == (hashValue % consumerConfig.getGroupSize()) ? row : null;
    }
  }

  /**
   * Performs initial scan for the given entry key. Scan will perform from the given entry key till the
   * end of entry represented by that stream file (i.e. offset = Long.MAX_VALUE).
   *
   * @param row the entry row key.
   */
  private void initScanIfNeeded(byte[] row) throws IOException {
    if (initScanned.contains(row)) {
      return;
    }

    // Scan till the end
    byte[] stopRow = Arrays.copyOf(row, row.length);

    // Last 8 bytes are the file offset, make it max value so that it scans till last offset.
    Bytes.putLong(stopRow, stopRow.length - Longs.BYTES, Long.MAX_VALUE);
    StateScanner scanner = scanStates(row, stopRow);
    try {
      while (scanner.nextStateRow()) {
        storeInitState(scanner.getRow(), scanner.getState());
      }

      initScanned.add(row);
    } finally {
      scanner.close();
    }
  }

  private void storeInitState(byte[] row, byte[] stateValue) {
    // Logic is adpated from QueueEntryRow.canConsume(), with modification.

    if (stateValue == null) {
      // State value shouldn't be null, as the row is only written with state value.
      return;
    }

    // If the state was updated by a different consumer instance that is still active, ignore this entry.
    // The assumption is, the corresponding instance is either processing (claimed)
    // or going to process it (due to rollback/restart).
    // This only applies to FIFO, as for hash and rr, repartition needs to happen if group size change.
    int stateInstanceId = QueueEntryRow.getStateInstanceId(stateValue);
    if (consumerConfig.getDequeueStrategy() == DequeueStrategy.FIFO
      && stateInstanceId < consumerConfig.getGroupSize()
      && stateInstanceId != consumerConfig.getInstanceId()) {
      ignoreRows.add(row);
    }

    long stateWritePointer = QueueEntryRow.getStateWritePointer(stateValue);

    // If state is PROCESSED and committed, ignore it:
    ConsumerEntryState state = QueueEntryRow.getState(stateValue);
    if (state == ConsumerEntryState.PROCESSED && transaction.isVisible(stateWritePointer)) {
      ignoreRows.add(row);
    }
  }

  /**
   * Scanner for scanning state table.
   */
  protected interface StateScanner extends Closeable {

    boolean nextStateRow() throws IOException;

    byte[] getRow();

    byte[] getState();
  }

  /**
   * Represents a {@link StreamEvent} created by the {@link #poll(int, long, java.util.concurrent.TimeUnit)} call.
   */
  private static final class PollStreamEvent extends ForwardingStreamEvent {

    private final byte[] stateRow;

    protected PollStreamEvent(StreamEventOffset delegate, byte[] stateRow) {
      super(delegate);
      this.stateRow = stateRow;
    }

    private byte[] getStateRow() {
      return stateRow;
    }
  }

  /**
   * A {@link DequeueResult} returned by {@link #poll(int, long, java.util.concurrent.TimeUnit)}.
   */
  private final class SimpleDequeueResult implements DequeueResult<StreamEvent> {

    private final List<PollStreamEvent> events;

    private SimpleDequeueResult(List<PollStreamEvent> events) {
      this.events = ImmutableList.copyOf(events);
    }

    @Override
    public boolean isEmpty() {
      return events.isEmpty();
    }

    @Override
    public void reclaim() {
      // Simply copy events back to polledEvents
      polledEvents.clear();
      polledEvents.addAll(events);
    }

    @Override
    public int size() {
      return events.size();
    }

    @Override
    public Iterator<StreamEvent> iterator() {
      return Iterators.transform(events.iterator(), CONVERT_STREAM_EVENT);
    }
  }
}