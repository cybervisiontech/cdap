/*
 * Copyright 2014 Cask, Inc.
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

package co.cask.cdap.data.dataset;

import co.cask.cdap.api.data.DataSetContext;
import co.cask.cdap.api.data.DataSetSpecification;

/**
 * Throwing data-set. Constructor throws exception.
 */
public class ThrowingDataSet extends IncompleteDataSet {
  public ThrowingDataSet(String name) {
    super(name);
  }

  @Override
  public void initialize(DataSetSpecification spec, DataSetContext context) {
    super.initialize(spec, context);
    throw new IllegalArgumentException("don't ever call me!");
  }
}