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

package co.cask.cdap.app.runtime;

import co.cask.cdap.app.program.Program;

/**
 *
 */
public interface ProgramRunner {

  /**
   * Prepare the {@link Program} with the given {@link ProgramOptions}.
   * This method must returns immediately and have the {@link ProgramController} returned
   * state management. After preparing program can be started from {@link ProgramController}.
   *
   * @param program
   * @param options
   * @return
   */
  //TODO: IT WILL BE RENAMED TO PREPARE
  ProgramController run(Program program, ProgramOptions options);
}
