/*
 * Copyright 2014 Continuuity,Inc. All Rights Reserved.
 */
package com.continuuity.data2.transaction.stream.hbase;

import com.continuuity.common.conf.CConfiguration;
import com.continuuity.data2.transaction.stream.AbstractStreamFileAdmin;
import com.continuuity.data2.transaction.stream.StreamConsumerStateStoreFactory;
import com.google.inject.Inject;
import org.apache.twill.filesystem.LocationFactory;

/**
 * A file based {@link com.continuuity.data2.transaction.stream.StreamAdmin} that uses HBase for maintaining
 * consumer state information.
 */
public final class HBaseStreamFileAdmin extends AbstractStreamFileAdmin {

  @Inject
  HBaseStreamFileAdmin(LocationFactory locationFactory, CConfiguration cConf,
                       StreamConsumerStateStoreFactory stateStoreFactory) {
    super(locationFactory, cConf, stateStoreFactory);
  }
}