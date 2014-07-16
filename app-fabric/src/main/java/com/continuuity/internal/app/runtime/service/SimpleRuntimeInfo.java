/*
 * Copyright 2012-2014 Continuuity, Inc.
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

package com.continuuity.internal.app.runtime.service;

import com.continuuity.api.metadata.Id;
import com.continuuity.api.metadata.ProgramType;
import com.continuuity.app.program.Program;
import com.continuuity.app.runtime.ProgramController;
import com.continuuity.app.runtime.ProgramRuntimeService;
import com.google.common.base.Objects;

/**
 *
 */
public final class SimpleRuntimeInfo implements ProgramRuntimeService.RuntimeInfo {

  private final ProgramController controller;
  private final ProgramType type;
  private final Id.Program programId;

  public SimpleRuntimeInfo(ProgramController controller, Program program) {
    this(controller,
         program.getType(),
         Id.Program.from(program.getAccountId(), program.getApplicationId(), program.getName()));
  }

  public SimpleRuntimeInfo(ProgramController controller, ProgramType type, Id.Program programId) {
    this.controller = controller;
    this.type = type;
    this.programId = programId;
  }

  @Override
  public ProgramController getController() {
    return controller;
  }

  @Override
  public ProgramType getType() {
    return type;
  }

  @Override
  public Id.Program getProgramId() {
    return programId;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(ProgramRuntimeService.RuntimeInfo.class)
      .add("type", type)
      .add("appId", programId.getApplicationId())
      .add("programId", programId.getId())
      .toString();
  }
}
