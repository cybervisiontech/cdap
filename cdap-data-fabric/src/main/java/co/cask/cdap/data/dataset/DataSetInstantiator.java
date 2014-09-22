/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.data.dataset;

import co.cask.cdap.api.data.DataSetContext;
import co.cask.cdap.api.data.DataSetInstantiationException;
import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.metrics.MeteredDataset;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.metrics.MetricsCollector;
import co.cask.cdap.data.Namespace;
import co.cask.cdap.data2.datafabric.DefaultDatasetNamespace;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.dataset2.NamespacedDatasetFramework;
import co.cask.tephra.TransactionAware;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The data set instantiator creates instances of data sets at runtime. It
 * must be called from the execution context to get operational instances
 * of data sets. Given a list of data set specs and a data fabric runtime it
 * can construct an instance of a data set and inject the data fabric runtime
 * into its base tables (and other built-in data sets).
 *
 * The instantiation and injection uses Java reflection a lot. This may look
 * unclean, but it helps us keep the DataSet API clean and simple: no need
 * to pass in data fabric runtime; no exposure of developers to the raw
 * data fabric; and developers only interact with Datasets.
 */
public class DataSetInstantiator implements DataSetContext {

  private static final Logger LOG = LoggerFactory.getLogger(DataSetInstantiator.class);
  private final DatasetFramework datasetFramework;
  // the class loader to use for data set classes
  private final ClassLoader classLoader;
  private final Set<TransactionAware> txAware = Sets.newIdentityHashSet();
  // in this collection we have only datasets initialized with getDataSet() which is OK for now...
  private final Map<TransactionAware, String> txAwareToMetricNames = Maps.newIdentityHashMap();

  private final MetricsCollector dsMetricsCollector;
  private final MetricsCollector programMetricsCollector;

  /**
   * Constructor from data fabric.
   * @param classLoader the class loader to use for loading data set classes.
   *                    If null, then the default class loader is used
   */
  public DataSetInstantiator(DatasetFramework datasetFramework,
                             CConfiguration configuration,
                             ClassLoader classLoader,
                             @Nullable
                             MetricsCollector dsMetricsCollector,
                             @Nullable
                             MetricsCollector programMetricsCollector) {
    this.classLoader = classLoader;
    this.dsMetricsCollector = dsMetricsCollector;
    this.programMetricsCollector = programMetricsCollector;
    // todo: should be passed in already namespaced. Refactor
    this.datasetFramework =
      new NamespacedDatasetFramework(datasetFramework,
                                     new DefaultDatasetNamespace(configuration, Namespace.USER));
  }

  @Override
  public <T extends Closeable> T getDataSet(String dataSetName)
    throws DataSetInstantiationException {
    return getDataSet(dataSetName, DatasetDefinition.NO_ARGUMENTS);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Closeable> T getDataSet(String name, Map<String, String> arguments)
    throws DataSetInstantiationException {
    T dataSet = (T) getDataset(name, arguments);
    if (dataSet == null) {
      throw logAndException(null, "No data set named %s can be instantiated.", name);
    }

    return dataSet;
  }

  private <T extends Dataset> T getDataset(String datasetName, Map<String, String> arguments)
    throws DataSetInstantiationException {

    T dataset;
    try {
      if (!datasetFramework.hasInstance(datasetName)) {
        throw new DataSetInstantiationException("Trying to access dataset that does not exist: " + datasetName);
      }

      dataset = datasetFramework.getDataset(datasetName, arguments, classLoader);
      if (dataset == null) {
        throw new DataSetInstantiationException("Failed to access dataset: " + datasetName);
      }

    } catch (Exception e) {
      throw new DataSetInstantiationException("Failed to access dataset: " + datasetName, e);
    }

    if (dataset instanceof TransactionAware) {
      txAware.add((TransactionAware) dataset);
      txAwareToMetricNames.put((TransactionAware) dataset, datasetName);
    }

    if (dataset instanceof MeteredDataset) {
      ((MeteredDataset) dataset).setMetricsCollector(new MetricsCollectorImpl(datasetName,
                                                                              dsMetricsCollector,
                                                                              programMetricsCollector));
    }

    return dataset;
  }

  /**
   * Returns an immutable life Iterable of {@link co.cask.tephra.TransactionAware} objects.
   */
  // NOTE: this is needed for now to minimize destruction of early integration of txds2
  public Iterable<TransactionAware> getTransactionAware() {
    return Iterables.unmodifiableIterable(txAware);
  }

  public void addTransactionAware(TransactionAware transactionAware) {
    txAware.add(transactionAware);
  }

  public void removeTransactionAware(TransactionAware transactionAware) {
    txAware.remove(transactionAware);
  }

  /**
   * Helper method to log a message and create an exception. The caller is
   * responsible for throwing the exception.
   */
  private DataSetInstantiationException logAndException(Throwable e, String message, Object... params)
    throws DataSetInstantiationException {
    String msg;
    DataSetInstantiationException exn;
    if (e == null) {
      msg = String.format("Error instantiating data set: %s", String.format(message, params));
      exn = new DataSetInstantiationException(msg);
      LOG.error(msg);
    } else {
      msg = String.format("Error instantiating data set: %s. %s", String.format(message, params), e.getMessage());
      if (e instanceof DataSetInstantiationException) {
        exn = (DataSetInstantiationException) e;
      } else {
        exn = new DataSetInstantiationException(msg, e);
      }
      LOG.error(msg, e);
    }
    return exn;
  }

  private static final class MetricsCollectorImpl implements MeteredDataset.MetricsCollector {
    private final String datasetName;
    private final MetricsCollector dataSetMetrics;
    private final MetricsCollector programContextMetrics;

    private MetricsCollectorImpl(String datasetName,
                                 @Nullable
                                 MetricsCollector dataSetMetrics,
                                 @Nullable
                                 MetricsCollector programContextMetrics) {
      this.datasetName = datasetName;
      this.dataSetMetrics = dataSetMetrics;
      this.programContextMetrics = programContextMetrics;
    }

    @Override
    public void recordRead(int opsCount, int dataSize) {
      if (programContextMetrics != null) {
        programContextMetrics.increment("store.reads", 1, datasetName);
        programContextMetrics.increment("store.ops", 1, datasetName);
      }
      // these metrics are outside the context of any application and will stay unless explicitly
      // deleted.  Useful for dataset metrics that must survive the deletion of application metrics.
      if (dataSetMetrics != null) {
        dataSetMetrics.increment("dataset.store.reads", 1, datasetName);
        dataSetMetrics.increment("dataset.store.ops", 1, datasetName);
      }
    }

    @Override
    public void recordWrite(int opsCount, int dataSize) {
      if (programContextMetrics != null) {
        programContextMetrics.increment("store.writes", 1, datasetName);
        programContextMetrics.increment("store.bytes", dataSize, datasetName);
        programContextMetrics.increment("store.ops", 1, datasetName);
      }
      // these metrics are outside the context of any application and will stay unless explicitly
      // deleted.  Useful for dataset metrics that must survive the deletion of application metrics.
      if (dataSetMetrics != null) {
        dataSetMetrics.increment("dataset.store.writes", 1, datasetName);
        dataSetMetrics.increment("dataset.store.bytes", dataSize, datasetName);
        dataSetMetrics.increment("dataset.store.ops", 1, datasetName);
      }
    }
  }
}