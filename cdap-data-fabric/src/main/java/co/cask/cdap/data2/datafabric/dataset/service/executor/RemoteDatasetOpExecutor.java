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

package co.cask.cdap.data2.datafabric.dataset.service.executor;

import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.discovery.EndpointStrategy;
import co.cask.cdap.common.discovery.RandomEndpointStrategy;
import co.cask.cdap.common.discovery.TimeLimitEndpointStrategy;
import co.cask.cdap.common.exception.HandlerException;
import co.cask.cdap.proto.DatasetTypeMeta;
import co.cask.common.http.HttpRequest;
import co.cask.common.http.HttpRequests;
import co.cask.common.http.HttpResponse;
import co.cask.common.http.ObjectResponse;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.gson.Gson;
import com.google.inject.Inject;
import org.apache.twill.discovery.Discoverable;
import org.apache.twill.discovery.DiscoveryServiceClient;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Executes Dataset operations by querying a {@link DatasetOpExecutorService} via REST.
 */
public abstract class RemoteDatasetOpExecutor extends AbstractIdleService implements DatasetOpExecutor {

  private static final Gson GSON = new Gson();

  private final Supplier<EndpointStrategy> endpointStrategySupplier;

  @Inject
  public RemoteDatasetOpExecutor(final DiscoveryServiceClient discoveryClient) {
    this.endpointStrategySupplier = Suppliers.memoize(new Supplier<EndpointStrategy>() {
      @Override
      public EndpointStrategy get() {
        return new TimeLimitEndpointStrategy(
          new RandomEndpointStrategy(
            discoveryClient.discover(Constants.Service.DATASET_EXECUTOR)), 2L, TimeUnit.SECONDS);
      }
    });
  }

  @Override
  public boolean exists(String instanceName) throws Exception {
    return (Boolean) executeAdminOp(instanceName, "exists").getResult();
  }

  @Override
  public DatasetSpecification create(String instanceName, DatasetTypeMeta typeMeta, DatasetProperties props)
    throws Exception {

    InternalDatasetCreationParams creationParams = new InternalDatasetCreationParams(typeMeta, props);
    HttpRequest request = HttpRequest.post(resolve(instanceName, "create"))
      .withBody(GSON.toJson(creationParams))
      .build();
    HttpResponse response = HttpRequests.execute(request);
    verifyResponse(response);

    return ObjectResponse.fromJsonBody(response, DatasetSpecification.class).getResponseObject();
  }

  @Override
  public void drop(DatasetSpecification spec, DatasetTypeMeta typeMeta) throws Exception {
    InternalDatasetDropParams dropParams = new InternalDatasetDropParams(typeMeta, spec);
    HttpRequest request = HttpRequest.post(resolve(spec.getName(), "drop")).withBody(GSON.toJson(dropParams)).build();
    HttpResponse response = HttpRequests.execute(request);
    verifyResponse(response);
  }

  @Override
  public void truncate(String instanceName) throws Exception {
    executeAdminOp(instanceName, "truncate");
  }

  @Override
  public void upgrade(String instanceName) throws Exception {
    executeAdminOp(instanceName, "upgrade");
  }

  private DatasetAdminOpResponse executeAdminOp(String instanceName, String opName)
    throws IOException, HandlerException {

    HttpResponse httpResponse = HttpRequests.execute(HttpRequest.post(resolve(instanceName, opName)).build());
    verifyResponse(httpResponse);

    return GSON.fromJson(new String(httpResponse.getResponseBody()), DatasetAdminOpResponse.class);
  }

  private URL resolve(String instanceName, String opName) throws MalformedURLException {
    return resolve(String.format("datasets/%s/admin/%s", instanceName, opName));
  }

  private URL resolve(String path) throws MalformedURLException {
    Discoverable endpoint = endpointStrategySupplier.get().pick();
    if (endpoint == null) {
      throw new IllegalStateException("No endpoint for " + Constants.Service.DATASET_EXECUTOR);
    }
    InetSocketAddress addr = endpoint.getSocketAddress();
    return new URL(String.format("http://%s:%s%s/data/%s",
                         addr.getHostName(), addr.getPort(),
                         Constants.Gateway.API_VERSION_2,
                         path));
  }

  private void verifyResponse(HttpResponse httpResponse) {
    if (httpResponse.getResponseCode() != 200) {
      throw new HandlerException(HttpResponseStatus.valueOf(httpResponse.getResponseCode()),
                                 httpResponse.getResponseMessage());
    }
  }
}
