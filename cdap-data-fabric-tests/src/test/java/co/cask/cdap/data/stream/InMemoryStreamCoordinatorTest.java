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
package co.cask.cdap.data.stream;

import co.cask.cdap.common.guice.ConfigModule;
import co.cask.cdap.common.guice.DiscoveryRuntimeModule;
import co.cask.cdap.common.guice.LocationRuntimeModule;
import co.cask.cdap.data.runtime.DataFabricModules;
import co.cask.cdap.data.runtime.DataSetsModules;
import co.cask.cdap.data.runtime.TransactionMetricsModule;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import org.junit.BeforeClass;

/**
 *
 */
public class InMemoryStreamCoordinatorTest extends StreamCoordinatorTestBase {

  private static Injector injector;

  @BeforeClass
  public static void init() {
    injector = Guice.createInjector(
      new ConfigModule(),
      new DiscoveryRuntimeModule().getInMemoryModules(),
      new DataFabricModules().getInMemoryModules(),
      new LocationRuntimeModule().getInMemoryModules(),
      new TransactionMetricsModule(),
      new AbstractModule() {
        @Override
        protected void configure() {
          bind(StreamCoordinator.class).to(InMemoryStreamCoordinator.class).in(Scopes.SINGLETON);
        }
      }
    );
  }

  @Override
  protected StreamCoordinator createStreamCoordinator() {
    return injector.getInstance(StreamCoordinator.class);
  }
}
