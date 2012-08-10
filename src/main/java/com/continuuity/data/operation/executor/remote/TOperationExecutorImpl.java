package com.continuuity.data.operation.executor.remote;

import com.continuuity.api.data.*;
import com.continuuity.data.operation.ClearFabric;
import com.continuuity.data.operation.executor.BatchOperationException;
import com.continuuity.data.operation.executor.BatchOperationResult;
import com.continuuity.data.operation.executor.OperationExecutor;
import com.continuuity.data.operation.executor.remote.stubs.*;
import com.continuuity.data.operation.ttqueue.*;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The implementation of a thrift service for operation execution.
 * All operations arrive over the wire as Thrift objects. We must
 * convert ("unwrap") them into data fabric operations, execute using
 * the actual operations executor, and send the results back as
 * ("wrapped") Thrift objects.
 *
 * Why all this conversion (wrap/unwrap), and not define all Operations
 * themselves as Thrift objects?
 * <ul><li>
 *   All the non-thrift executors would have to use the Thrift objects
 * </li><li>
 *   Thrift's object model is too restrictive: it has only limited inheritance
 *   and no overloading
 * </li><li>
 *   Thrift objects are bare-bone, all they have are getters, setters, and
 *   basic object methods.
 * </li></ul>
 */
public class TOperationExecutorImpl
    extends ConverterUtils
    implements TOperationExecutor.Iface {

  private static final Logger Log =
      LoggerFactory.getLogger(TOperationExecutorImpl.class);

  /** the operation executor to use for all operations */
  private OperationExecutor opex;

  /** constructor requires the operation executor */
  public TOperationExecutorImpl(OperationExecutor opex) {
    this.opex = opex;
  }

  // Write operations. They all return a boolean, which is
  // always safe to return with Thrift.

  @Override
  public boolean write(TWrite tWrite) throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TWrite: " + tWrite);
    Write write = unwrap(tWrite);
    boolean success = this.opex.execute(write);
    if (Log.isDebugEnabled())
      Log.debug("Write result: " + success);
    return success;
  }

  @Override
  public boolean delet(TDelete tDelete) throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TDelete: " + tDelete);
    Delete delete = unwrap(tDelete);
    boolean success = this.opex.execute(delete);
    if (Log.isDebugEnabled())
      Log.debug("Delete result: " + success);
    return success;
  }

  @Override
  public boolean increment(TIncrement tIncrement) throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TIncrement: " + tIncrement);
    Increment increment = unwrap(tIncrement);
    boolean success = this.opex.execute(increment);
    if (Log.isDebugEnabled())
      Log.debug("Increment result: " + success);
    return success;
  }

  @Override
  public boolean compareAndSwap(TCompareAndSwap tCompareAndSwap)
      throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TCompareAndSwap: " + tCompareAndSwap);
    CompareAndSwap compareAndSwap = unwrap(tCompareAndSwap);
    boolean success = this.opex.execute(compareAndSwap);
    if (Log.isDebugEnabled())
      Log.debug("CompareAndSwap result: " + success);
    return success;
  }

  @Override
  public boolean queueEnqueue(TQueueEnqueue tQueueEnqueue) throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TQueueEnqueue: " + tQueueEnqueue);
    QueueEnqueue queueEnqueue = unwrap(tQueueEnqueue);
    boolean success = this.opex.execute(queueEnqueue);
    if (Log.isDebugEnabled())
      Log.debug("QueueEnqueue result: " + success);
    return success;
  }

  @Override
  public boolean queueAck(TQueueAck tQueueAck) throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TQueueAck: " + tQueueAck);
    QueueAck queueAck = unwrap(tQueueAck);
    boolean success = this.opex.execute(queueAck);
    if (Log.isDebugEnabled())
      Log.debug("QueueAck result: " + success);
    return success;
  }

  // batch write, return a structure and never null, and is thus safe

  @Override
  public TBatchOperationResult batch(List<TWriteOperation> batch)
      throws TBatchOperationException, TException {
    if (Log.isDebugEnabled())
      Log.debug("Received Batch");
    List<WriteOperation> writes = new ArrayList<WriteOperation>(batch.size());
    for (TWriteOperation tWriteOp : batch) {
      WriteOperation writeOp;
      if (tWriteOp.isSetWrite())
        writeOp = unwrap(tWriteOp.getWrite());
      else if (tWriteOp.isSetDelet())
        writeOp = unwrap(tWriteOp.getDelet());
      else if (tWriteOp.isSetIncrement())
        writeOp = unwrap(tWriteOp.getIncrement());
      else if (tWriteOp.isSetCompareAndSwap())
        writeOp = unwrap(tWriteOp.getCompareAndSwap());
      else if (tWriteOp.isSetQueueEnqueue())
        writeOp = new QueueEnqueue(tWriteOp.getQueueEnqueue().getQueueName(),
            tWriteOp.getQueueEnqueue().getValue());
      else if (tWriteOp.isSetQueueAck())
        writeOp = unwrap(tWriteOp.getQueueAck());
      else {
        Log.error("Internal Error: Unkown TWriteOperation "
            + tWriteOp.toString() + " in batch. Skipping.");
        continue;
      }
      if (Log.isDebugEnabled())
        Log.debug("Operation in batch: " + writeOp);
      writes.add(writeOp);
    }
    BatchOperationResult result;
    try {
      result = this.opex.execute(writes);
    } catch (BatchOperationException e) {
      throw new TBatchOperationException(e.getMessage());
    }
    if (Log.isDebugEnabled())
      Log.debug("Batch result: " + result);
    return new TBatchOperationResult(result.isSuccess(), result.getMessage());
  }

  // read operations. they may return null from the executor.
  // Because Thrift methods cannot return null, we must wrap their
  // results into a structure

  @Override
  public TOptionalBinary readKey(TReadKey tReadKey) throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TReadKey: " + tReadKey);
    ReadKey readKey = unwrap(tReadKey);
    byte[] result = this.opex.execute(readKey);
    if (Log.isDebugEnabled())
      Log.debug("ReadKey result: "
        + (result == null ? "<null>" : Arrays.toString(result)));
    return wrapBinary(result);
  }

  @Override
  public TOptionalBinaryMap read(TRead tRead) throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TRead: " + tRead);
    Read read = unwrap(tRead);
    Map<byte[], byte[]> result = this.opex.execute(read);
    TOptionalBinaryMap tResult = wrapMap(result);
    if (Log.isDebugEnabled())
      Log.debug("Read result: " + tResult);
    return tResult;
  }

  @Override
  public TOptionalBinaryList readAllKeys(TReadAllKeys tReadAllKeys)
      throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TReadAllKeys: " + tReadAllKeys);
    ReadAllKeys readAllKeys = unwrap(tReadAllKeys);
    List<byte[]> result = this.opex.execute(readAllKeys);
    TOptionalBinaryList tResult = wrapList(result);
    if (Log.isDebugEnabled())
      Log.debug("ReadAllKeys result: " + tResult);
    return tResult;
  }

  @Override
  public TOptionalBinaryMap
  readColumnRange(TReadColumnRange tReadColumnRange) throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TReadColumnRange: " + tReadColumnRange);
    ReadColumnRange readColumnRange = unwrap(tReadColumnRange);
    Map<byte[], byte[]> result = this.opex.execute(readColumnRange);
    TOptionalBinaryMap tResult = wrapMap(result);
    if (Log.isDebugEnabled())
      Log.debug("ReadColumnRange result: " + tResult);
    return tResult;
  }

  // dequeue always return a structure, which does not need extra wrapping

  @Override
  public TDequeueResult dequeue(TQueueDequeue tQueueDequeue) throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TQueueDequeue" + tQueueDequeue.toString());
    QueueDequeue queueDequeue = unwrap(tQueueDequeue);
    DequeueResult result = this.opex.execute(queueDequeue);
    if (Log.isDebugEnabled())
      Log.debug("QueueDequeue result: " + result);
    return wrap(result);
  }

  // getGroupId always returns a long and cannot be null

  @Override
  public long getGroupId(TGetGroupId tGetGroupId) throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TGetGroupID: " + tGetGroupId);
    QueueAdmin.GetGroupID getGroupID = unwrap(tGetGroupId);
    long groupId = this.opex.execute(getGroupID);
    if (Log.isDebugEnabled())
      Log.debug("GetGroupID result: " + groupId);
    return groupId;
  }

  // getQueueMeta can return null, if the queue does not exist

  @Override
  public TQueueMeta getQueueMeta(TGetQueueMeta tGetQueueMeta)
      throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TGetQueueMeta: " + tGetQueueMeta);
    QueueAdmin.GetQueueMeta getQueueMeta = unwrap(tGetQueueMeta);
    QueueAdmin.QueueMeta queueMeta = this.opex.execute(getQueueMeta);
    if (Log.isDebugEnabled())
      Log.debug("GetQueueMeta result: " +
          (queueMeta == null ? "<null>" : queueMeta.toString()));
    return wrap(queueMeta);
  }

  // clearFabric is safe as it returns nothing

  @Override
  public void clearFabric(TClearFabric tClearFabric) throws TException {
    if (Log.isDebugEnabled())
      Log.debug("Received TClearFabric: " + tClearFabric);
    ClearFabric clearFabric = unwrap(tClearFabric);
    this.opex.execute(clearFabric);
    if (Log.isDebugEnabled())
      Log.debug("ClearFabric completed.");
  }
}
