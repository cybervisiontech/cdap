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

package co.cask.cdap.data2.dataset.lib.table.hbase;

import co.cask.cdap.api.dataset.table.Row;
import co.cask.cdap.api.dataset.table.Scanner;
import com.continuuity.tephra.Transaction;
import com.google.common.base.Throwables;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;

import java.io.IOException;
import java.util.Map;

/**
 * Implements Scanner on top of HBase resultSetScanner.
 */
public class HBaseScanner implements Scanner {

  private final ResultScanner scanner;
  private final Transaction tx;

  public HBaseScanner(ResultScanner scanner, Transaction tx) {
    this.scanner = scanner;
    this.tx = tx;
  }

  @Override
  public Row next() {
    if (scanner == null) {
      return null;
    }

    try {

      //Loop until one row is read completely or until end is reached.
      while (true) {
        Result result = scanner.next();
        if (result == null || result.isEmpty()) {
          break;
        }

        Map<byte[], byte[]> rowMap = HBaseOcTableClient.getRowMap(result, tx);
        if (rowMap.size() > 0) {
          return new co.cask.cdap.data2.dataset2.lib.table.Result(result.getRow(), rowMap);
        }
      }

      // exhausted
      return null;

    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public void close() {
    scanner.close();
  }
}