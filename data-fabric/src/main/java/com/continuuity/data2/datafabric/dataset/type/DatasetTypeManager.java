package com.continuuity.data2.datafabric.dataset.type;

import com.continuuity.data2.dataset2.manager.DatasetManager;
import com.continuuity.data2.transaction.DefaultTransactionExecutor;
import com.continuuity.data2.transaction.TransactionAware;
import com.continuuity.data2.transaction.TransactionExecutor;
import com.continuuity.data2.transaction.TransactionFailureException;
import com.continuuity.data2.transaction.TransactionSystemClient;
import com.continuuity.internal.data.dataset.DatasetAdmin;
import com.continuuity.internal.data.dataset.DatasetDefinition;
import com.continuuity.common.lang.jar.JarClassLoader;
import com.continuuity.internal.data.dataset.DatasetInstanceProperties;
import com.continuuity.internal.data.dataset.lib.table.OrderedTable;
import com.continuuity.internal.data.dataset.module.DatasetModule;
import com.continuuity.internal.data.dataset.module.DatasetDefinitionRegistry;
import com.continuuity.data2.dataset2.manager.inmemory.InMemoryDatasetDefinitionRegistry;
import com.continuuity.internal.lang.ClassLoaders;
import com.google.common.util.concurrent.AbstractIdleService;
import org.apache.twill.filesystem.Location;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.apache.twill.filesystem.LocationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Manages dataset types and modules metadata
 */
// todo: there's ugly work with Datasets & Transactions (incl. exceptions inside txnl code) which will be revised as
//       part of open-sourcing Datasets effort
public class DatasetTypeManager extends AbstractIdleService {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetTypeManager.class);

  private final TransactionSystemClient txClient;
  private final DatasetManager mdsDatasetManager;
  private final LocationFactory locationFactory;
  /** guards {@link #mds} */
  private final Object mdsGuard = new Object();

  /** dataset types metadata store */
  private DatasetTypeMDS mds;
  private TransactionAware txAware;

  /**
   * Ctor
   * @param mdsDatasetManager dataset manager to be used to access the metadata store
   * @param txSystemClient tx client to be used to operate on the metadata store
   */
  public DatasetTypeManager(DatasetManager mdsDatasetManager,
                            TransactionSystemClient txSystemClient,
                            LocationFactory locationFactory) {
    this.mdsDatasetManager = mdsDatasetManager;
    this.txClient = txSystemClient;
    this.locationFactory = locationFactory;
  }

  @Override
  protected void startUp() throws Exception {
    // todo: once namespacing is implemented in new datasets "continuuity.system" should go away from here
    OrderedTable table = getMDSTable(mdsDatasetManager, "continuuity.system.datasets.instance");
    this.txAware = (TransactionAware) table;
    this.mds = new DatasetTypeMDS(table);
  }

  @Override
  protected void shutDown() throws Exception {
    mds.close();
  }

  /**
   * Add datasets module
   * @param name module name
   * @param className module class
   * @param jarLocation location of the module jar
   */
  public void addModule(final String name, final String className, final Location jarLocation)
    throws DatasetModuleConflictException {

    LOG.info("adding module, name: {}, className: {}, jarLocation: {}", name, className, jarLocation);

    try {
      getTxExecutor().executeUnchecked(new TransactionExecutor.Subroutine() {
        @Override
        public void apply() throws DatasetModuleConflictException {
          synchronized (mdsGuard) {
            DatasetModuleMeta existing = mds.getModule(name);
            if (existing != null) {
              String msg = String.format("cannot add module %s, module with the same name already exists: %s",
                                         name, existing);
              LOG.warn(msg);
              throw new DatasetModuleConflictException(msg);
            }

            DatasetDefinitionRegistry registry = new InMemoryDatasetDefinitionRegistry();
            ClassLoader cl;
            DatasetModule module;
            try {
              // NOTE: we assume all classes needed to load dataset module class are available in the jar or otherwise
              //       are system classes
              cl = new JarClassLoader(jarLocation);
              @SuppressWarnings("unchecked")
              Class<DatasetModule> moduleClass = (Class<DatasetModule>) ClassLoaders.loadClass(className, cl, this);
              module = moduleClass.newInstance();
            } catch (Exception e) {
              throw Throwables.propagate(e);
            }

            DependencyTrackingRegistry reg = new DependencyTrackingRegistry(registry, cl);
            module.register(reg);

            List<String> moduleDependencies = Lists.newArrayList();
            for (String usedType : reg.getUsedTypes()) {
              DatasetModuleMeta usedModule = mds.getModuleByType(usedType);
              // adding all used types and the module itself, in this very order to keep the order of loading modules
              // for instantiating a type
              moduleDependencies.addAll(usedModule.getUsesModules());
              moduleDependencies.add(usedModule.getName());
              // also adding this module as a dependent for all modules it uses
              usedModule.addUsedByModule(name);
              mds.write(usedModule);
            }

            DatasetModuleMeta moduleMeta = new DatasetModuleMeta(name, className, jarLocation.toURI(),
                                                                 reg.getTypes(), moduleDependencies);
            mds.write(moduleMeta);
          }
        }
      });
    } catch (UncheckedExecutionException e) {
      if (e.getCause() != null && e.getCause() instanceof DatasetModuleConflictException) {
        throw (DatasetModuleConflictException) e.getCause();
      }
      throw e;
    }
  }

  /**
   * @param datasetTypeName name of the type
   * @return instance of {@link DatasetDefinition} of the given type or {@code null} if one doesn't exist
   */
  @Nullable
  public DatasetDefinition getType(final String datasetTypeName) {
    return getTxExecutor().executeUnchecked(new Callable<DatasetDefinition>() {
      @Override
      public DatasetDefinition call() throws Exception {
        DatasetTypeMeta type = null;
        synchronized (mdsGuard) {
          type = mds.getType(datasetTypeName);
        }
        if (type == null) {
          return null;
        }
        return new DatasetDefinitionLoader(locationFactory).load(type);
      }
    });
  }

  /**
   * @return collection of types available in the system
   */
  public Collection<DatasetTypeMeta> getTypes() {
    return getTxExecutor().executeUnchecked(new Callable<Collection<DatasetTypeMeta>>() {
      @Override
      public Collection<DatasetTypeMeta> call() throws Exception {
        synchronized (mdsGuard) {
          return mds.getTypes();
        }
      }
    });
  }

  /**
   * Get dataset type information
   * @param typeName name of the type to get info for
   * @return instance of {@link com.continuuity.data2.datafabric.dataset.type.DatasetTypeMeta} or {@code null} if type
   *         does NOT exist
   */
  @Nullable
  public DatasetTypeMeta getTypeInfo(final String typeName) {
    return getTxExecutor().executeUnchecked(new Callable<DatasetTypeMeta>() {
      @Override
      public DatasetTypeMeta call() throws Exception {
        synchronized (mdsGuard) {
          return mds.getType(typeName);
        }
      }
    });
  }

  /**
   * @return list of dataset modules information
   */
  public Collection<DatasetModuleMeta> getModules() {
    return getTxExecutor().executeUnchecked(new Callable<Collection<DatasetModuleMeta>>() {
      @Override
      public Collection<DatasetModuleMeta> call() throws Exception {
        synchronized (mdsGuard) {
          return mds.getModules();
        }
      }
    });
  }

  /**
   * @param name of the module to return info for
   * @return dataset module info or {@code null} if module with given name does NOT exist
   */
  @Nullable
  public DatasetModuleMeta getModule(final String name) {
    return getTxExecutor().executeUnchecked(new Callable<DatasetModuleMeta>() {
      @Override
      public DatasetModuleMeta call() throws Exception {
        synchronized (mdsGuard) {
          return mds.getModule(name);
        }
      }
    });
  }

  /**
   * Deletes specified dataset module
   * @param name name of dataset module to delete
   * @return true if deleted successfully, false if module didn't exist: nothing to delete
   * @throws DatasetModuleConflictException when there are other modules depend on the specified one, in which case
   *         deletion does NOT happen
   */
  public boolean deleteModule(final String name) throws DatasetModuleConflictException {
    LOG.info("Deleting module {}", name);
    try {
      return getTxExecutor().execute(new Callable<Boolean>() {
        @Override
        public Boolean call() throws DatasetModuleConflictException {
          synchronized (mdsGuard) {
            DatasetModuleMeta module = mds.getModule(name);

            if (module == null) {
              return false;
            }

            // cannot delete when there's module that uses it
            if (module.getUsedByModules().size() > 0) {
              String msg =
                String.format("Cannot delete module %s: other modules depend on it. Delete them first", module);
              throw new DatasetModuleConflictException(msg);
            }

            // remove it from "usedBy" from other modules
            for (String usedModuleName : module.getUsesModules()) {
              DatasetModuleMeta usedModule = mds.getModule(usedModuleName);
              usedModule.removeUsedByModule(name);
              mds.write(usedModule);
            }

            mds.deleteModule(name);
          }

          return true;
        }
      });
    } catch (TransactionFailureException e) {
      if (e.getCause() != null && e.getCause() instanceof DatasetModuleConflictException) {
        throw (DatasetModuleConflictException) e.getCause();
      }
      throw Throwables.propagate(e);
    }
  }

  private TransactionExecutor getTxExecutor() {
    return new DefaultTransactionExecutor(txClient, ImmutableList.of(txAware));
  }

  private OrderedTable getMDSTable(DatasetManager datasetManager, String mdsTable) {
    try {
      DatasetAdmin admin = datasetManager.getAdmin(mdsTable);
      try {
        if (admin == null) {
          datasetManager.addInstance("orderedTable", mdsTable, DatasetInstanceProperties.EMPTY);
          admin = datasetManager.getAdmin(mdsTable);
          if (admin == null) {
            throw new RuntimeException("Cannot add instance of a table " + mdsTable);
          }
        }

        if (!admin.exists()) {
          admin.create();
        }

        return (OrderedTable) datasetManager.getDataset(mdsTable);
      } finally {
        if (admin != null) {
          admin.close();
        }
      }
    } catch (Exception e) {
      LOG.error("Could not get access to MDS table", e);
      throw Throwables.propagate(e);
    }
  }

  private class DependencyTrackingRegistry implements DatasetDefinitionRegistry {
    private final DatasetDefinitionRegistry delegate;
    private final ClassLoader classLoader;

    private final List<String> types = Lists.newArrayList();
    private final List<String> usedTypes = Lists.newArrayList();

    public DependencyTrackingRegistry(DatasetDefinitionRegistry delegate, ClassLoader classLoader) {
      this.delegate = delegate;
      this.classLoader = classLoader;
    }

    public List<String> getTypes() {
      return types;
    }

    public List<String> getUsedTypes() {
      return usedTypes;
    }

    @Override
    public void add(DatasetDefinition def) {
      types.add(def.getName());
      delegate.add(def);
    }

    @Override
    public <T extends DatasetDefinition> T get(String datasetTypeName) {
      usedTypes.add(datasetTypeName);
      return delegate.get(datasetTypeName);
    }

    public ClassLoader getClassLoader() {
      return classLoader;
    }
  }
}