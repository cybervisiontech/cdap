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
package co.cask.cdap.metrics.data;

/**
 * Class to represent a timestamp in seconds and an int value.
 */
public final class TimeValue {

  private final long time;
  private final long value;

  public TimeValue(long time, long value) {
    this.time = time;
    this.value = value;
  }

  public long getTime() {
    return time;
  }

  public long getValue() {
    return value;
  }
}
