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

package co.cask.cdap.client;

import co.cask.cdap.api.flow.flowlet.StreamEvent;
import co.cask.cdap.client.config.ClientConfig;
import co.cask.cdap.client.exception.BadRequestException;
import co.cask.cdap.client.exception.StreamNotFoundException;
import co.cask.cdap.client.exception.UnAuthorizedAccessTokenException;
import co.cask.cdap.client.util.RESTClient;
import co.cask.cdap.common.stream.StreamEventTypeAdapter;
import co.cask.cdap.proto.StreamProperties;
import co.cask.cdap.proto.StreamRecord;
import co.cask.cdap.security.authentication.client.AccessToken;
import co.cask.common.http.HttpMethod;
import co.cask.common.http.HttpRequest;
import co.cask.common.http.HttpRequests;
import co.cask.common.http.HttpResponse;
import co.cask.common.http.ObjectResponse;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.core.HttpHeaders;

/**
 * Provides ways to interact with CDAP Streams.
 */
public class StreamClient {

  private static final Gson GSON = StreamEventTypeAdapter.register(new GsonBuilder()).create();

  private final RESTClient restClient;
  private final ClientConfig config;

  @Inject
  public StreamClient(ClientConfig config) {
    this.config = config;
    this.restClient = RESTClient.create(config);
  }

  /**
   * Gets the configuration of a stream.
   *
   * @param streamId ID of the stream
   * @throws IOException if a network error occurred
   * @throws StreamNotFoundException if the stream was not found
   */
  public StreamProperties getConfig(String streamId) throws IOException, StreamNotFoundException,
    UnAuthorizedAccessTokenException {
    URL url = config.resolveURL(String.format("streams/%s/info", streamId));
    HttpResponse response = restClient.execute(HttpMethod.GET, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_NOT_FOUND);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new StreamNotFoundException(streamId);
    }
    return ObjectResponse.fromJsonBody(response, StreamProperties.class).getResponseObject();
  }

  /**
   * Creates a stream.
   *
   * @param newStreamId ID of the new stream to create
   * @throws IOException if a network error occurred
   * @throws BadRequestException if the provided stream ID was invalid
   */
  public void create(String newStreamId) throws IOException, BadRequestException, UnAuthorizedAccessTokenException {
    URL url = config.resolveURL(String.format("streams/%s", newStreamId));
    HttpResponse response = restClient.execute(HttpMethod.PUT, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_BAD_REQUEST);
    if (response.getResponseCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
      throw new BadRequestException("Bad request: " + response.getResponseBodyAsString());
    }
  }

  /**
   * Sends an event to a stream.
   *
   * @param streamId ID of the stream
   * @param event event to send to the stream
   * @throws IOException if a network error occurred
   * @throws StreamNotFoundException if the stream with the specified ID was not found
   */
  public void sendEvent(String streamId, String event) throws IOException,
                                                              StreamNotFoundException,
                                                              UnAuthorizedAccessTokenException {
    writeEvent(config.resolveURL(String.format("streams/%s", streamId)), streamId, event);
  }

  /**
   * Sends an event to a stream. The writes is asynchronous, meaning when this method returns, it only guarantees
   * the event has been received by the server, but may not get persisted.
   *
   * @param streamId ID of the stream
   * @param event event to send to the stream
   * @throws IOException if a network error occurred
   * @throws StreamNotFoundException if the stream with the specified ID was not found
   */
  public void asyncSendEvent(String streamId, String event) throws IOException,
                                                                   StreamNotFoundException,
                                                                   UnAuthorizedAccessTokenException {
    writeEvent(config.resolveURL(String.format("streams/%s/async", streamId)), streamId, event);
  }

  /**
   * Truncates a stream, deleting all stream events belonging to the stream.
   *
   * @param streamId ID of the stream to truncate
   * @throws IOException if a network error occurred
   * @throws StreamNotFoundException if the stream with the specified name was not found
   */
  public void truncate(String streamId) throws IOException, StreamNotFoundException, UnAuthorizedAccessTokenException {
    URL url = config.resolveURL(String.format("streams/%s/truncate", streamId));
    HttpResponse response = restClient.execute(HttpMethod.POST, url, config.getAccessToken(),
                                               HttpURLConnection.HTTP_NOT_FOUND);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new StreamNotFoundException(streamId);
    }
  }

  /**
   * Sets the Time-to-Live (TTL) of a stream. TTL governs how long stream events are readable.
   *
   * @param streamId ID of the stream
   * @param ttlInSeconds desired TTL, in seconds
   * @throws IOException if a network error occurred
   * @throws StreamNotFoundException if the stream with the specified name was not found
   */
  public void setTTL(String streamId, long ttlInSeconds) throws IOException, StreamNotFoundException,
    UnAuthorizedAccessTokenException {
    URL url = config.resolveURL(String.format("streams/%s/config", streamId));
    HttpRequest request = HttpRequest.put(url).withBody(GSON.toJson(ImmutableMap.of("ttl", ttlInSeconds))).build();

    HttpResponse response = restClient.execute(request, config.getAccessToken(), HttpURLConnection.HTTP_NOT_FOUND);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new StreamNotFoundException(streamId);
    }
  }

  /**
   * Lists all streams.
   *
   * @return list of {@link StreamRecord}s
   * @throws IOException if a network error occurred
   */
  public List<StreamRecord> list() throws IOException, UnAuthorizedAccessTokenException {
    URL url = config.resolveURL("streams");
    HttpResponse response = restClient.execute(HttpMethod.GET, url, config.getAccessToken());
    return ObjectResponse.fromJsonBody(response, new TypeToken<List<StreamRecord>>() { }).getResponseObject();
  }

  /**
   * Reads events from a stream
   *
   * @param streamId ID of the stream
   * @param startTime Timestamp in milliseconds to start reading event from (inclusive)
   * @param endTime Timestamp in milliseconds for the last event to read (exclusive)
   * @param limit Maximum number of events to read
   * @param results Collection for storing the resulting stream events
   * @param <T> Type of the collection for storing results
   * @return The same collection object as passed in the {@code results} parameter
   * @throws IOException If fails to read from stream
   * @throws StreamNotFoundException If the given stream does not exists
   */
  public <T extends Collection<? super StreamEvent>> T getEvents(String streamId, long startTime,
                                                                 long endTime, int limit, final T results)
                                                                 throws IOException, StreamNotFoundException {
    getEvents(streamId, startTime, endTime, limit, new Function<StreamEvent, Boolean>() {
      @Override
      public Boolean apply(StreamEvent input) {
        results.add(input);
        return true;
      }
    });
    return results;
  }

  /**
   * Reads events from a stream
   *
   * @param streamId ID of the stream
   * @param startTime Timestamp in milliseconds to start reading event from (inclusive)
   * @param endTime Timestamp in milliseconds for the last event to read (exclusive)
   * @param limit Maximum number of events to read
   * @param callback Callback to invoke for each stream event read. If the callback function returns {@code false}
   *                 upon invocation, it will stops the reading
   * @throws IOException If fails to read from stream
   * @throws StreamNotFoundException If the given stream does not exists
   */
  public void getEvents(String streamId, long startTime, long endTime, int limit,
                        Function<? super StreamEvent, Boolean> callback) throws IOException, StreamNotFoundException {
    URL url = config.resolveURL(String.format("streams/%s/events?start=%d&end=%d&limit=%d",
                                              streamId, startTime, endTime, limit));
    HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
    AccessToken accessToken = config.getAccessToken();
    if (accessToken != null) {
      urlConn.setRequestProperty(HttpHeaders.AUTHORIZATION, accessToken.getTokenType() + " " + accessToken.getValue());
    }

    if (urlConn instanceof HttpsURLConnection && !config.isVerifySSLCert()) {
      try {
        HttpRequests.disableCertCheck((HttpsURLConnection) urlConn);
      } catch (Exception e) {
        // TODO: Log "Got exception while disabling SSL certificate check for request.getURL()"
      }
    }

    try {
      if (urlConn.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
        throw new StreamNotFoundException(streamId);
      }
      if (urlConn.getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT) {
        return;
      }

      // The response is an array of stream event object
      JsonReader jsonReader = new JsonReader(new InputStreamReader(urlConn.getInputStream(), Charsets.UTF_8));
      jsonReader.beginArray();
      while (jsonReader.peek() != JsonToken.END_ARRAY) {
        Boolean result = callback.apply(GSON.<StreamEvent>fromJson(jsonReader, StreamEvent.class));
        if (result == null || !result) {
          break;
        }
      }
      // No need to close reader, the urlConn.disconnect in finally will close all underlying streams
    } finally {
      urlConn.disconnect();
    }
  }

  /**
   * Writes stream event using the given URL. The write maybe sync or async, depending on the URL.
   */
  private void writeEvent(URL url, String streamId, String event) throws IOException,
                                                                         StreamNotFoundException,
                                                                         UnAuthorizedAccessTokenException {
    HttpRequest request = HttpRequest.post(url).withBody(event).build();
    HttpResponse response = restClient.execute(request, config.getAccessToken(), HttpURLConnection.HTTP_NOT_FOUND);
    if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new StreamNotFoundException(streamId);
    }
  }
}
