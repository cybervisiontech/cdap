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

package co.cask.cdap.api.service;

import co.cask.cdap.api.data.DataSetContext;
import co.cask.cdap.api.dataset.Dataset;

/**
 * A runnable that provides a {@link DataSetContext} to {@link ServiceWorker}s which may be used to get
 * access to and use datasets.
 */
public interface TxRunnable {

  /**
   * Provides a {@link DataSetContext} to get instances of {@link Dataset}s.
   *
   * <p>
   *   Operations executed on a dataset within the execution of this method are committed as a single transaction.
   *   The transaction is started before {@link #run(DataSetContext)} is invoked and is committed upon successful
   *   execution. Exceptions thrown while committing the transaction or thrown by user-code result in a rollback of the
   *   transaction.
   * </p>
   * @param context to get datasets from.
   * @throws Exception
   */
  void run(DataSetContext context) throws Exception;

}