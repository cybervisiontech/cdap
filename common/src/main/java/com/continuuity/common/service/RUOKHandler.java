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

package com.continuuity.common.service;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * Standard ruok command handler. Usage example:
 * {@code
 *     cmdService = CommandPortService.builder("my-service")
 *     .setPort(port)
 *     .addCommandHandler(RUOKHandler.COMMAND, RUOKHandler.DESCRIPTION, new RUOKHandler())
 *     .build();
 * }
 */
public class RUOKHandler implements CommandPortService.CommandHandler {
  public static final String COMMAND = "ruok";
  public static final String DESCRIPTION = "ruok";

  @Override
  public void handle(BufferedWriter respondWriter) throws IOException {
    respondWriter.write("imok");
    respondWriter.close();
  }
}
