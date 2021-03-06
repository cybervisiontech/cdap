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
package co.cask.cdap.gateway.handlers.procedure;

import com.ning.http.client.Request;
import org.jboss.netty.buffer.ChannelBuffer;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An implementation of {@link Request.EntityWriter} for sending request body from
 * a {@link ChannelBuffer}.
 */
public final class ChannelBufferEntityWriter implements Request.EntityWriter {

  private final ChannelBuffer buffer;

  public ChannelBufferEntityWriter(ChannelBuffer buffer) {
    this.buffer = buffer;
  }

  @Override
  public void writeEntity(OutputStream out) throws IOException {
    buffer.readBytes(out, buffer.readableBytes());
  }
}
