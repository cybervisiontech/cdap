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

package co.cask.cdap.internal.app.deploy.pipeline;

import co.cask.cdap.app.ApplicationSpecification;
import co.cask.cdap.proto.Id;
import org.apache.twill.filesystem.Location;

/**
 * This class carries information about ApplicationSpecification
 * and Location between stages.
 */
public class ApplicationSpecLocation {
  private final ApplicationSpecification specification;
  private final Location archive;
  private final Id.Application id;

  public ApplicationSpecLocation(Id.Application id, ApplicationSpecification specification, Location archive) {
    this.id = id;
    this.specification = specification;
    this.archive = archive;
  }

  /**
   * @return {@link ApplicationSpecification} sent to this stage.
   */
  public ApplicationSpecification getSpecification() {
    return specification;
  }

  /**
   * @return Location of archive to this stage.
   */
  public Location getArchive() {
    return archive;
  }

  /**
   * @return Application Id
   */
  public Id.Application getApplicationId() {
    return id;
  }
}