/*
 * Copyright 2014 Continuuity,Inc. All Rights Reserved.
 */
package com.continuuity.data2.dataset2.lib.hbase;

import com.continuuity.api.common.Bytes;
import com.continuuity.common.utils.ProjectInfo;
import com.continuuity.data2.util.hbase.HBaseTableUtil;
import com.continuuity.internal.data.dataset.DatasetAdmin;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.Coprocessor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableNotEnabledException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.twill.filesystem.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for writing HBase DataSetManager.
 */
public abstract class AbstractHBaseDataSetAdmin implements DatasetAdmin {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractHBaseDataSetAdmin.class);

  // Property key in the coprocessor for storing version of the coprocessor.
  private static final String CONTINUUITY_VERSION = "continuuity.version";

  // Function to convert Class into class Name
  private static final Function<Class<?>, String> CLASS_TO_NAME = new Function<Class<?>, String>() {
    @Override
    public String apply(Class<?> input) {
      return input.getName();
    }
  };

  protected final String tableName;
  protected final HBaseAdmin admin;
  protected final HBaseTableUtil tableUtil;

  protected AbstractHBaseDataSetAdmin(String name, HBaseAdmin admin, HBaseTableUtil tableUtil) {
    this.tableName = name;
    this.admin = admin;
    this.tableUtil = tableUtil;
  }

  // todo
//  @Override
  public void upgrade() throws IOException {
    // todo: do we need HBaseTableUtil.getHBaseTableName
    upgradeTable(HBaseTableUtil.getHBaseTableName(tableName));
  }

  /**
   * Performs upgrade on a given HBase table.
   *
   * @param tableNameStr The HBase table name that upgrade will be performed on.
   * @throws IOException If upgrade failed.
   */
  protected void upgradeTable(String tableNameStr) throws IOException {
    byte[] tableName = Bytes.toBytes(tableNameStr);

    HTableDescriptor tableDescriptor = admin.getTableDescriptor(tableName);

    // Get the continuuity version from the table
    ProjectInfo.Version version = new ProjectInfo.Version(tableDescriptor.getValue(CONTINUUITY_VERSION));
    if (version.compareTo(ProjectInfo.getVersion()) >= 0) {
      // If the table has greater than or same version, no need to upgrade.
      LOG.info("Table '{}' was upgraded with same or newer version '{}'. Current version is '{}'",
               tableNameStr, version, ProjectInfo.getVersion());
      return;
    }

    // Upgrade any table properties if necessary
    boolean needUpgrade = upgradeTable(tableDescriptor);

    // Generate the coprocessor jar
    CoprocessorJar coprocessorJar = createCoprocessorJar();
    Location jarLocation = coprocessorJar.getJarLocation();

    // Check if coprocessor upgrade is needed
    Map<String, HBaseTableUtil.CoprocessorInfo> coprocessorInfo = HBaseTableUtil.getCoprocessorInfo(tableDescriptor);

    // For all required coprocessors, check if they've need to be upgraded.
    for (Class<? extends Coprocessor> coprocessor : coprocessorJar.getCoprocessors()) {
      HBaseTableUtil.CoprocessorInfo info = coprocessorInfo.get(coprocessor.getName());
      if (info != null) {
        // The same coprocessor has been configured, check by the file name hash to see if they are the same.
        if (!jarLocation.getName().equals(info.getPath().getName())) {
          needUpgrade = true;
          // Remove old one and add the new one.
          tableDescriptor.removeCoprocessor(info.getClassName());
          addCoprocessor(tableDescriptor, coprocessor, jarLocation);
        }
      } else {
        // The coprocessor is missing from the table, add it.
        needUpgrade = true;
        addCoprocessor(tableDescriptor, coprocessor, jarLocation);
      }
    }

    // Removes all old coprocessors
    Set<String> coprocessorNames = ImmutableSet.copyOf(Iterables.transform(coprocessorJar.coprocessors, CLASS_TO_NAME));
    for (String remove : Sets.difference(coprocessorInfo.keySet(), coprocessorNames)) {
      needUpgrade = true;
      tableDescriptor.removeCoprocessor(remove);
    }

    if (!needUpgrade) {
      LOG.info("No upgrade needed for table '{}'", tableNameStr);
      return;
    }

    // Add the current version as table properties only if the table needs upgrade
    tableDescriptor.setValue(CONTINUUITY_VERSION, ProjectInfo.getVersion().toString());

    LOG.info("Upgrading table '{}'...", tableNameStr);
    boolean enableTable = false;
    try {
      admin.disableTable(tableName);
      enableTable = true;
    } catch (TableNotEnabledException e) {
      LOG.debug("Table '{}' not enabled when try to disable it.", tableNameStr);
    }

    admin.modifyTable(tableName, tableDescriptor);
    if (enableTable) {
      admin.enableTable(tableName);
    }

    LOG.info("Table '{}' upgrade completed.", tableNameStr);
  }

  protected void addCoprocessor(HTableDescriptor tableDescriptor, Class<? extends Coprocessor> coprocessor,
                                Location jarFile) throws IOException {
    tableDescriptor.addCoprocessor(coprocessor.getName(), new Path(jarFile.toURI()), Coprocessor.PRIORITY_USER, null);
  }

  protected abstract CoprocessorJar createCoprocessorJar() throws IOException;

  /**
   * Modifies the table descriptor for upgrade.
   *
   * @return true if the table descriptor is modified.
   */
  protected abstract boolean upgradeTable(HTableDescriptor tableDescriptor);

  /**
   * Holder for coprocessor information.
   */
  protected static final class CoprocessorJar {
    public static final CoprocessorJar EMPTY = new CoprocessorJar(ImmutableList.<Class<? extends Coprocessor>>of(),
                                                                  null);

    private final List<Class<? extends Coprocessor>> coprocessors;
    private final Location jarLocation;

    public CoprocessorJar(Iterable<? extends Class<? extends Coprocessor>> coprocessors, Location jarLocation) {
      this.coprocessors = ImmutableList.copyOf(coprocessors);
      this.jarLocation = jarLocation;
    }

    public Iterable<? extends Class<? extends Coprocessor>> getCoprocessors() {
      return coprocessors;
    }

    public Location getJarLocation() {
      return jarLocation;
    }

    public boolean isEmpty() {
      return coprocessors.isEmpty();
    }

    public int size() {
      return coprocessors.size();
    }
  }
}