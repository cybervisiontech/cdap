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

package co.cask.cdap.cli.command;

import co.cask.cdap.cli.ElementType;
import co.cask.cdap.cli.util.AsciiTable;
import co.cask.cdap.cli.util.RowMaker;
import co.cask.cdap.client.StreamClient;
import co.cask.cdap.proto.StreamRecord;
import co.cask.common.cli.Arguments;
import co.cask.common.cli.Command;
import com.google.inject.Inject;

import java.io.PrintStream;

/**
 * Lists streams.
 */
public class ListStreamsCommand implements Command {

  private final StreamClient streamClient;

  @Inject
  public ListStreamsCommand(StreamClient streamClient) {
    this.streamClient = streamClient;
  }

  @Override
  public void execute(Arguments arguments, PrintStream output) throws Exception {
    new AsciiTable<StreamRecord>(
      new String[] { "name" },
      streamClient.list(),
      new RowMaker<StreamRecord>() {
        @Override
        public Object[] makeRow(StreamRecord object) {
          return new String[]{object.getId()};
        }
      }).print(output);
  }

  @Override
  public String getPattern() {
    return "list streams";
  }

  @Override
  public String getDescription() {
    return "Lists " + ElementType.STREAM.getPluralPrettyName();
  }
}
