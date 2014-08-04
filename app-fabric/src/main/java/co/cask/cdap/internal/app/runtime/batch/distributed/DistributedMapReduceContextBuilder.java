/*
 * Copyright 2014 Cask, Inc.
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

package co.cask.cdap.internal.app.runtime.batch.distributed;

import co.cask.cdap.api.dataset.module.DatasetDefinitionRegistry;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.guice.ConfigModule;
import co.cask.cdap.common.guice.DiscoveryRuntimeModule;
import co.cask.cdap.common.guice.IOModule;
import co.cask.cdap.common.guice.LocationRuntimeModule;
import co.cask.cdap.common.guice.ZKClientModule;
import co.cask.cdap.data.DataSetAccessor;
import co.cask.cdap.data.DistributedDataSetAccessor;
import co.cask.cdap.data2.datafabric.dataset.RemoteDatasetFramework;
import co.cask.cdap.data2.datafabric.dataset.type.DatasetTypeClassLoaderFactory;
import co.cask.cdap.data2.datafabric.dataset.type.DistributedDatasetTypeClassLoaderFactory;
import co.cask.cdap.data2.dataset2.DatasetDefinitionRegistryFactory;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.dataset2.DefaultDatasetDefinitionRegistry;
import co.cask.cdap.data2.util.hbase.HBaseTableUtil;
import co.cask.cdap.data2.util.hbase.HBaseTableUtilFactory;
import co.cask.cdap.gateway.auth.AuthModule;
import co.cask.cdap.internal.app.runtime.ProgramServiceDiscovery;
import co.cask.cdap.internal.app.runtime.batch.AbstractMapReduceContextBuilder;
import co.cask.cdap.internal.app.runtime.distributed.DistributedProgramServiceDiscovery;
import co.cask.cdap.logging.appender.LogAppender;
import co.cask.cdap.logging.appender.LogAppenderInitializer;
import co.cask.cdap.logging.appender.kafka.KafkaLogAppender;
import co.cask.cdap.metrics.guice.MetricsClientRuntimeModule;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.twill.zookeeper.ZKClientService;

/**
 * Builds an instance of {@link co.cask.cdap.internal.app.runtime.batch.BasicMapReduceContext} good for
 * distributed environment. The context is to be used in remote worker (e.g. Mapper task started in YARN container)
 */
public class DistributedMapReduceContextBuilder extends AbstractMapReduceContextBuilder {
  private final CConfiguration cConf;
  private final Configuration hConf;
  private final TaskAttemptContext taskContext;
  private ZKClientService zkClientService;

  public DistributedMapReduceContextBuilder(CConfiguration cConf, Configuration hConf, TaskAttemptContext taskContext) {
    this.cConf = cConf;
    this.hConf = hConf;
    this.taskContext = taskContext;
  }

  @Override
  protected Injector prepare() {
    Injector injector = Guice.createInjector(
      new ConfigModule(cConf, hConf),
      new LocationRuntimeModule().getDistributedModules(),
      new IOModule(),
      new AuthModule(),
      new ZKClientModule(),
      new DiscoveryRuntimeModule().getDistributedModules(),
      new MetricsClientRuntimeModule().getMapReduceModules(taskContext),
      new AbstractModule() {
        @Override
        protected void configure() {

          // Data-fabric bindings
          bind(HBaseTableUtil.class).toProvider(HBaseTableUtilFactory.class);

          // txds2
          bind(DataSetAccessor.class).to(DistributedDataSetAccessor.class).in(Singleton.class);

          install(new FactoryModuleBuilder()
                    .implement(DatasetDefinitionRegistry.class, DefaultDatasetDefinitionRegistry.class)
                    .build(DatasetDefinitionRegistryFactory.class));
          bind(DatasetTypeClassLoaderFactory.class).to(DistributedDatasetTypeClassLoaderFactory.class);
          bind(DatasetFramework.class).to(RemoteDatasetFramework.class);

          // For log publishing
          bind(LogAppender.class).to(KafkaLogAppender.class);

          //install discovery service modules
          bind(ProgramServiceDiscovery.class).to(DistributedProgramServiceDiscovery.class).in(Scopes.SINGLETON);
        }
      }
    );

    zkClientService = injector.getInstance(ZKClientService.class);
    zkClientService.start();

    // todo: we should do this in AbstractMapReduceContextBuilder, see REACTOR-682
    // Initialize log appender
    LogAppenderInitializer logAppenderInitializer = injector.getInstance(LogAppenderInitializer.class);
    logAppenderInitializer.initialize();

    return injector;
  }

  @Override
  protected void finish() {
    zkClientService.stop();
  }
}