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

package co.cask.cdap.test.app;

import co.cask.cdap.test.TestBase;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Set;

/**
 * This test and DummyBaseCloneTest make sure that two TestBase classes can be executed in the same suite -
 * in particular, it makes sure that Explore is working properly.
 */
public class DummyBaseTestRun extends TestBase {
  @Test
  public void test() throws Exception {
    deployApplication(DummyApp.class);
    Connection connection = getQueryClient();
    try {
      Set<String> tables = Sets.newHashSet();
      ResultSet resultSet = connection.prepareStatement("show tables").executeQuery();
      try {
        while (resultSet.next()) {
          tables.add(resultSet.getString(1));
        }
      } finally {
        resultSet.close();
      }
      Assert.assertEquals(Sets.newHashSet("cdap_stream_who", "cdap_user_whom"), tables);
    } finally {
      connection.close();
      clear();
    }
  }
}
