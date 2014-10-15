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

package co.cask.cdap.internal.app;

import co.cask.cdap.api.service.http.HttpServiceSpecification;
import co.cask.cdap.internal.service.http.DefaultHttpServiceSpecification;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

/**
 * GSON codec to serialize/deserialize {@link HttpServiceSpecification}.
 */
public class HttpServiceSpecificationCodec extends AbstractSpecificationCodec<HttpServiceSpecification> {
  @Override
  public HttpServiceSpecification deserialize(JsonElement json, Type typeOfT,
                                              JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObj = json.getAsJsonObject();

    String className = jsonObj.get("className").getAsString();
    String name = jsonObj.get("name").getAsString();
    String description = jsonObj.get("description").getAsString();
    Map<String, String> properties = deserializeMap(jsonObj.get("properties"), context, String.class);
    Set<String> datasets = deserializeSet(jsonObj.get("datasets"), context, String.class);

    return new DefaultHttpServiceSpecification(className, name, description, properties, datasets);
  }

  @Override
  public JsonElement serialize(HttpServiceSpecification src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject json = new JsonObject();
    json.addProperty("className", src.getClassName());
    json.addProperty("name", src.getName());
    json.addProperty("description", src.getDescription());
    json.add("properties", serializeMap(src.getProperties(), context, String.class));
    json.add("datasets", serializeSet(src.getDatasets(), context, String.class));

    return json;
  }
}
