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

package co.cask.cdap.cli.completer.element;

import co.cask.cdap.api.service.http.ServiceHttpEndpoint;
import co.cask.cdap.cli.CLIMain;
import co.cask.cdap.cli.completer.StringsCompleter;
import co.cask.cdap.client.ServiceClient;
import co.cask.cdap.client.exception.NotFoundException;
import co.cask.cdap.client.exception.UnAuthorizedAccessTokenException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.util.List;
import java.util.TreeSet;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Completer for service endpoints.
 */
public class ServiceEndpointCompleter extends StringsCompleter {

  private ServiceClient serviceClient;
  private String app;
  private String service;

  @Inject
  public ServiceEndpointCompleter(final ServiceClient serviceClient) {
    super(Lists.<String>newArrayList());
    this.serviceClient = serviceClient;
  }

  @Override
  public TreeSet<String> getStrings() {
    try {
      List<ServiceHttpEndpoint> endpoints = serviceClient.getEndpoints(app, service);
      TreeSet<String> paths = Sets.newTreeSet();
      for (ServiceHttpEndpoint endpoint : endpoints) {
        paths.add(endpoint.getPath());
      }
      return paths;
    } catch (IOException e) {
      return Sets.newTreeSet();
    } catch (UnAuthorizedAccessTokenException e) {
      return Sets.newTreeSet();
    } catch (NotFoundException e) {
      return Sets.newTreeSet();
    }
  }

  @Override
  public int complete(@Nullable String buffer, int cursor, List<CharSequence> candidates) {
    String buff = CLIMain.getCLI().getReader().getCursorBuffer().buffer.toString();
    String[] input = buff.trim().split(" ");
    String[] appInfo = input[input.length - 2].split("\\.");
    if (appInfo.length > 1) {
      this.app = appInfo[0];
      this.service = appInfo[1];
    }
    return super.complete(buffer, cursor, candidates);
  }
}
