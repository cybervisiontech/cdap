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

package co.cask.cdap.proto;

import com.google.common.base.Objects;

/**
 * Represents metadata for namespaces
 */
public final class NamespaceMeta {
  private final String id;
  private final String displayName;
  private final String description;

  private NamespaceMeta(String id, String displayName, String description) {
    this.id = id;
    this.displayName = displayName;
    this.description = description;
  }

  public String getId() {
    return id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }

  /**
   * Builder used to build {@link NamespaceMeta}
   */
  public static final class Builder {
    private String id;
    private String displayName;
    private String description;

    public Builder setId(final String id) {
      this.id = id;
      return this;
    }

    public Builder setDisplayName(final String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder setDescription(final String description) {
      this.description = description;
      return this;
    }

    public NamespaceMeta build() {
      return new NamespaceMeta(id, displayName, description);
    }
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
      .add("id", id)
      .add("displayName", displayName)
      .add("description", description)
      .toString();
  }
}
