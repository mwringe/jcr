/*
 * Copyright (C) 2009 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.services.jcr.impl.storage.jdbc.init;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by The eXo Platform SAS
 * 
 * 26.03.2007
 * 
 * Ingres convert all db object names to lower case, so respect it.
 * Same as PgSQL initializer.
 * 
 * @author <a href="mailto:peter.nedonosko@exoplatform.com.ua">Peter Nedonosko</a>
 * @version $Id: IngresSQLDBInitializer.java 34801 2009-07-31 15:44:50Z dkatayev $
 */
public class IngresSQLDBInitializer extends StorageDBInitializer
{

   public IngresSQLDBInitializer(String containerName, Connection connection, String scriptPath, boolean multiDb)
      throws IOException
   {
      super(containerName, connection, scriptPath, multiDb);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected boolean isIndexExists(Connection conn, String tableName, String indexName) throws SQLException
   {
      return super.isIndexExists(conn, tableName.toUpperCase().toLowerCase(), indexName.toUpperCase().toLowerCase());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected boolean isTableExists(Connection conn, String tableName) throws SQLException
   {
      return super.isTableExists(conn, tableName.toUpperCase().toLowerCase());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected boolean isSequenceExists(Connection conn, String sequenceName) throws SQLException
   {
      String seqName = sequenceName.toUpperCase().toLowerCase();
      try
      {
         ResultSet srs = conn.createStatement().executeQuery("SELECT NEXT VALUE FOR " + seqName);
         if (srs.next())
         {
            return true;
         }
         srs.close();
         return false;
      }
      catch (final SQLException e)
      {
         // check if sequence does not exist
         if (e.getMessage().indexOf("DEFINE CURSOR") >= 0 && e.getMessage().indexOf("Sequence") >= 0)
            return false;
         throw new SQLException(e.getMessage())
         {

            /**
             * {@inheritDoc}
             */
            @Override
            public Throwable getCause()
            {
               return e;
            }
         };
      }
   }

}
