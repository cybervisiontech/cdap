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
package co.cask.cdap.data2.transaction.stream.leveldb;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.guice.ConfigModule;
import co.cask.cdap.common.guice.LocationRuntimeModule;
import co.cask.cdap.data.runtime.DataFabricLevelDBModule;
import co.cask.cdap.data.runtime.TransactionMetricsModule;
import co.cask.cdap.data.stream.StreamFileWriterFactory;
import co.cask.cdap.data2.queue.QueueClientFactory;
import co.cask.cdap.data2.transaction.stream.StreamAdmin;
import co.cask.cdap.data2.transaction.stream.StreamConsumerFactory;
import co.cask.cdap.data2.transaction.stream.StreamConsumerTestBase;
import co.cask.tephra.TransactionManager;
import co.cask.tephra.TransactionSystemClient;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

/**
 *
 */
public class LevelDBStreamConsumerTest extends StreamConsumerTestBase {

  @ClassRule
  public static TemporaryFolder tmpFolder = new TemporaryFolder();

  private static CConfiguration cConf;
  private static StreamConsumerFactory consumerFactory;
  private static StreamAdmin streamAdmin;
  private static TransactionSystemClient txClient;
  private static TransactionManager txManager;
  private static QueueClientFactory queueClientFactory;
  private static StreamFileWriterFactory fileWriterFactory;

  @BeforeClass
  public static void init() throws Exception {
    cConf = CConfiguration.create();
    cConf.set(Constants.CFG_LOCAL_DATA_DIR, tmpFolder.newFolder().getAbsolutePath());

    Injector injector = Guice.createInjector(
      new ConfigModule(cConf),
      new LocationRuntimeModule().getInMemoryModules(),
      new DataFabricLevelDBModule(),
      new TransactionMetricsModule()
    );

    consumerFactory = injector.getInstance(StreamConsumerFactory.class);
    streamAdmin = injector.getInstance(StreamAdmin.class);
    txClient = injector.getInstance(TransactionSystemClient.class);
    txManager = injector.getInstance(TransactionManager.class);
    queueClientFactory = injector.getInstance(QueueClientFactory.class);
    fileWriterFactory = injector.getInstance(StreamFileWriterFactory.class);

    txManager.startAndWait();
  }

  @AfterClass
  public static void finish() throws Exception {
    txManager.stopAndWait();
  }

  @Override
  protected QueueClientFactory getQueueClientFactory() {
    return queueClientFactory;
  }

  @Override
  protected StreamConsumerFactory getConsumerFactory() {
    return consumerFactory;
  }

  @Override
  protected StreamAdmin getStreamAdmin() {
    return streamAdmin;
  }

  @Override
  protected TransactionSystemClient getTransactionClient() {
    return txClient;
  }

  @Override
  protected StreamFileWriterFactory getFileWriterFactory() {
    return fileWriterFactory;
  }
}
