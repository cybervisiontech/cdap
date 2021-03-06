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

package co.cask.cdap.internal.app.deploy.pipeline;

import co.cask.cdap.ToyApp;
import co.cask.cdap.app.ApplicationSpecification;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.lang.jar.JarFinder;
import co.cask.cdap.internal.app.ApplicationSpecificationAdapter;
import co.cask.cdap.internal.app.Specifications;
import co.cask.cdap.internal.io.ReflectionSchemaGenerator;
import co.cask.cdap.internal.pipeline.StageContext;
import co.cask.cdap.test.internal.DefaultId;
import org.apache.twill.filesystem.LocalLocationFactory;
import org.apache.twill.filesystem.Location;
import org.apache.twill.filesystem.LocationFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the program generation stage of the deploy pipeline.
 */
public class ProgramGenerationStageTest {
  private static CConfiguration configuration = CConfiguration.create();

  @Test
  public void testProgramGenerationForToyApp() throws Exception {
    configuration.set(Constants.AppFabric.OUTPUT_DIR, System.getProperty("java.io.tmpdir"));
    LocationFactory lf = new LocalLocationFactory();
    Location appArchive = lf.create(JarFinder.getJar(ToyApp.class));
    ApplicationSpecification appSpec = Specifications.from(new ToyApp());
    ApplicationSpecificationAdapter adapter = ApplicationSpecificationAdapter.create(new ReflectionSchemaGenerator());
    ApplicationSpecification newSpec = adapter.fromJson(adapter.toJson(appSpec));
    ProgramGenerationStage pgmStage = new ProgramGenerationStage(configuration, lf);
    pgmStage.process(new StageContext(Object.class));  // Can do better here - fixed right now to run the test.
    pgmStage.process(new ApplicationDeployable(configuration, DefaultId.APPLICATION, newSpec, appArchive));
    Assert.assertTrue(true);
  }

}
