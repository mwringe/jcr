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
package org.exoplatform.services.jcr.impl.clean.rdbms;

import org.exoplatform.commons.utils.SecurityHelper;
import org.exoplatform.services.jcr.core.security.JCRRuntimePermissions;
import org.exoplatform.services.jcr.impl.util.jdbc.DBInitializer;
import org.exoplatform.services.jcr.impl.util.jdbc.DBInitializerHelper;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.security.PrivilegedExceptionAction;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The goal of this class is removing workspace data from database.
 *
 * @author <a href="karpenko.sergiy@gmail.com">Karpenko Sergiy</a> 
 * @version $Id: DBCleaner.java 3769 2011-01-04 15:36:06Z areshetnyak $
 */
public class DBCleaner
{
   /**
    * Logger.
    */
   protected final static Log LOG = ExoLogger.getLogger("exo.jcr.component.core.DBClean");

   /**
    * Connection to database.
    */
   protected final Connection connection;

   /**
    * Pattern for JCR tables.
    */
   protected final Pattern dbObjectNamePattern;

   /**
    * Common clean scripts for database.
    */
   protected final List<String> cleanScripts = new ArrayList<String>();

   /**
    * Rollback scripts for database.
    */
   protected final List<String> rollbackScripts = new ArrayList<String>();

   /**
    * Commit scripts for database.
    */
   protected final List<String> commitScripts = new ArrayList<String>();

   /**
    * DB clean helper.
    */
   protected final DBCleanHelper dbCleanHelper;

   /**
    * DBCleaner constructor.
    * 
    * @param connection 
    *          connection to database where workspace tables is placed
    * @param cleanScripts
    *          scripts for cleaning database         
    */
   public DBCleaner(Connection connection, List<String> cleanScripts)
   {
      this(connection, cleanScripts, null);
   }

   /**
    * DBCleaner constructor.
    * 
    * @param connection 
    *          connection to database where workspace tables is placed
    * @param cleanScripts
    *          scripts for cleaning database         
    * @param dbCleanHelper
    *          class which help to clean database by executing special queries
    */
   public DBCleaner(Connection connection, List<String> cleanScripts, DBCleanHelper dbCleanHelper)
   {
      this.dbObjectNamePattern = Pattern.compile(DBInitializer.SQL_OBJECTNAME, Pattern.CASE_INSENSITIVE);
      this.connection = connection;
      this.cleanScripts.addAll(cleanScripts);
      this.dbCleanHelper = dbCleanHelper;
   }

   /**
    * DBCleaner constructor.
    * 
    * @param connection 
    *          connection to database where workspace tables is placed
    * @param cleanScripts
    *          scripts for cleaning database
    * @param rollbackScripts
    *          scripts for execution when something failed         
    * @param commitScripts
    *          scripts for removing temporary objects         
    * @param dbCleanHelper
    *          class which help to clean database by executing special queries
    */
   public DBCleaner(Connection connection, List<String> cleanScripts, List<String> rollbackScripts,
      List<String> commitScripts, DBCleanHelper cleanHelper)
   {
      this(connection, cleanScripts, cleanHelper);
      this.rollbackScripts.addAll(rollbackScripts);
      this.commitScripts.addAll(commitScripts);
   }

   /**
    * DBCleaner constructor.
    * 
    * @param connection 
    *          connection to database where workspace tables is placed
    * @param cleanScripts
    *          scripts for cleaning database
    * @param rollbackScripts
    *          scripts for execution when something failed         
    * @param commitScripts
    *          scripts for removing temporary objects         
    */
   public DBCleaner(Connection connection, List<String> cleanScripts, List<String> rollbackScripts,
      List<String> commitScripts)
   {
      this(connection, cleanScripts, rollbackScripts, commitScripts, null);
   }

   /**
    * Clean data from database. The method doesn't close connection or perform commit.
    * 
    * @throws SQLException
    *          if any errors occurred 
    */
   public void executeCleanScripts() throws SQLException
   {
      executeScripts(cleanScripts, false);

      if (dbCleanHelper != null)
      {
         dbCleanHelper.executeCleanScripts();
      }
   }

   /** 
    * Rollback changes. The method doesn't close connection or perform commit.
    *
    * @throws SQLException
    *          if any errors occurred 
    */
   public void executeRollbackScripts() throws SQLException
   {
      executeScripts(rollbackScripts, true);
   }

   /**
    * Cleaning temporary objects. The method doesn't close connection or perform commit.
    *
    * @throws SQLException
    *          if any errors occurred 
    */
   public void executeCommitScripts() throws SQLException
   {
      executeScripts(commitScripts, false);
   }

   /**
    * Execute script on database.  
    * 
    * @param scripts
    *          the scripts for execution 
    * @param  isSkipSQLExceprion
    *          boolean, skipping SQLException on rollback.  
    * @throws SQLException
    *          if any exception occurred
    */
   protected void executeScripts(List<String> scripts, boolean isSkipSQLExceprion) throws SQLException
   {
      SecurityManager security = System.getSecurityManager();
      if (security != null)
      {
         security.checkPermission(JCRRuntimePermissions.MANAGE_REPOSITORY_PERMISSION);
      }

      Statement st = connection.createStatement();
      try
      {
         for (String scr : scripts)
         {
            String sql = DBInitializerHelper.cleanWhitespaces(scr.trim());
            if (sql.length() > 0)
            {
               if (LOG.isDebugEnabled())
               {
                  LOG.debug("Execute script: \n[" + sql + "]");
               }
               
               try
               {
                  executeQuery(st, sql);
               } 
               catch (SQLException e)
               {
                  if (isSkipSQLExceprion)
                  {
                     LOG.warn("Execute script fail: \n[" + sql + "]");
                     continue;
                  }
                  else
                  {
                     throw e;
                  }
               }
            }
         }
      }
      finally
      {
         try
         {
            st.close();
         }
         catch (SQLException e)
         {
            LOG.error("Can't close the Statement." + e);
         }
      }
   }

   /**
    * Execute query.
    */
   protected void executeQuery(final Statement statement, final String sql) throws SQLException
   {
      SecurityHelper.doPrivilegedSQLExceptionAction(new PrivilegedExceptionAction<Object>()
      {
         public Object run() throws Exception
         {
            statement.executeUpdate(sql);
            return null;
         }
      });
   }
}
