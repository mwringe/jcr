/*
 * Copyright (C) 2003-2010 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.services.jcr.impl.core;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.exoplatform.services.jcr.access.AccessManager;
import org.exoplatform.services.jcr.config.RepositoryConfigurationException;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.config.WorkspaceEntry;
import org.exoplatform.services.jcr.datamodel.NodeData;
import org.exoplatform.services.jcr.impl.Constants;
import org.exoplatform.services.jcr.impl.core.nodetype.NodeTypeManagerImpl;
import org.exoplatform.services.jcr.impl.core.value.ValueFactoryImpl;
import org.exoplatform.services.jcr.impl.dataflow.persistent.CacheableWorkspaceDataManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Created by The eXo Platform SAS.
 * 
 * <br/>Date: 2010
 *
 * @author <a href="mailto:alex.reshetnyak@exoplatform.com.ua">Alex Reshetnyak</a> 
 * @version $Id$
 */
public class RdbmsBackupWorkspaceInitializer
   extends RdbmsWorkspaceInitializer
{
   /**
    * Logger.
    */
   protected static final Log log = ExoLogger.getLogger("exo.jcr.component.core.RdbmsBackupWorkspaceInitializer");

   /**
    * Constructor RdbmsBackupWorkspaceInitializer.
    */
   public RdbmsBackupWorkspaceInitializer(WorkspaceEntry config, RepositoryEntry repConfig,
            CacheableWorkspaceDataManager dataManager, NamespaceRegistryImpl namespaceRegistry,
            LocationFactory locationFactory, NodeTypeManagerImpl nodeTypeManager, ValueFactoryImpl valueFactory,
            AccessManager accessManager) throws RepositoryConfigurationException, PathNotFoundException,
            RepositoryException
   {
      super(config, repConfig, dataManager, namespaceRegistry, locationFactory, nodeTypeManager, valueFactory,
               accessManager);
   }


   /**
    * {@inheritDoc}
    */
   public NodeData initWorkspace() throws RepositoryException
   {
      if (isWorkspaceInitialized())
      {
         return (NodeData) dataManager.getItemData(Constants.ROOT_UUID);
      }

      long start = System.currentTimeMillis();

      rdbmsRestore();

      // restore from incremental backup
      incrementalRead();

      final NodeData root = (NodeData) dataManager.getItemData(Constants.ROOT_UUID);

      log.info("Workspace [" + workspaceName + "] restored from storage " + restorePath + " in "
               + (System.currentTimeMillis() - start) * 1d / 1000 + "sec");

      return root;
   }

}