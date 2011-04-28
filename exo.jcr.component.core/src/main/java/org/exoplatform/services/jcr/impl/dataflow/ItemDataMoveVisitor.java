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
package org.exoplatform.services.jcr.impl.dataflow;

import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.core.nodetype.NodeTypeDataManager;
import org.exoplatform.services.jcr.dataflow.ItemDataTraversingVisitor;
import org.exoplatform.services.jcr.dataflow.ItemState;
import org.exoplatform.services.jcr.datamodel.InternalQName;
import org.exoplatform.services.jcr.datamodel.NodeData;
import org.exoplatform.services.jcr.datamodel.PropertyData;
import org.exoplatform.services.jcr.datamodel.QPath;
import org.exoplatform.services.jcr.datamodel.ValueData;
import org.exoplatform.services.jcr.impl.Constants;
import org.exoplatform.services.jcr.impl.core.SessionDataManager;
import org.exoplatform.services.jcr.util.IdGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import javax.jcr.RepositoryException;

/**
 * The class visits each node, all subnodes and all of them properties. It transfer as parameter of
 * a method <code>ItemData.visits()</code>. During visiting the class forms the <b>itemAddStates</b>
 * list of <code>List&lt;ItemState&gt;</code> for copying new nodes and their properties and
 * <b>itemDeletedStates</b> for deleting existing nodes and properties.
 * 
 * @version $Id$
 */
public class ItemDataMoveVisitor extends ItemDataTraversingVisitor
{
   /**
    * The list of added item states
    */
   protected List<ItemState> deleteStates = new ArrayList<ItemState>();

   /**
    * Destination node name
    */
   private InternalQName destNodeName;

   /**
    * The stack. In the top it contains a parent node.
    */
   protected Stack<NodeData> parents;

   /**
    * The list of added item states
    */
   protected List<ItemState> addStates = new ArrayList<ItemState>();

   /**
    * The variable shows necessity of preservation <code>Identifier</code>, not generate new one, at
    * transformation of <code>Item</code>.
    */
   protected boolean keepIdentifiers;

   /**
    * The NodeTypeManager
    */
   protected NodeTypeDataManager ntManager;

   protected QPath ancestorToSave = null;

   /** 
    * Trigger events for descendents. 
   */
   protected boolean triggerEventsForDescendents;

   /**
    * Creates an instance of this class.
    * 
    * @param parent
    *          - The parent node
    * @param dstNodeName
    *          Destination node name
    * @param nodeTypeManager
    *          - The NodeTypeManager
    * @param srcDataManager
    *          - Source data manager
    * @param keepIdentifiers
    *          - Is it necessity to keep <code>Identifiers</code>
    * @param triggerEventsForDescendents 
    *          - Trigger events for descendents.          
    */
   public ItemDataMoveVisitor(NodeData parent, InternalQName dstNodeName, NodeTypeDataManager nodeTypeManager,
      SessionDataManager srcDataManager, boolean keepIdentifiers, boolean triggerEventsForDescendents)
   {
      super(srcDataManager, triggerEventsForDescendents ? INFINITE_DEPTH : 0);
      this.keepIdentifiers = keepIdentifiers;
      this.ntManager = nodeTypeManager;
      this.destNodeName = dstNodeName;

      this.parents = new Stack<NodeData>();
      this.parents.add(parent);
      this.triggerEventsForDescendents = triggerEventsForDescendents;
   }

   /** 
    * Creates an instance of this class. 
    * 
    * @param parent - The parent node 
    * @param dstNodeName Destination node name 
    * @param nodeTypeManager - The NodeTypeManager 
    * @param srcDataManager - Source data manager 
    * @param keepIdentifiers - Is it necessity to keep <code>Identifiers</code> 
    * @param skipEventsForDescendents - Don't generate events for the 
    *          descendants. 
    */
   public ItemDataMoveVisitor(NodeData parent, InternalQName dstNodeName, NodeTypeDataManager nodeTypeManager,
      SessionDataManager srcDataManager, boolean keepIdentifiers)
   {
      this(parent, dstNodeName, nodeTypeManager, srcDataManager, keepIdentifiers, false);
   }

   @Override
   protected void entering(NodeData node, int level) throws RepositoryException
   {

      if (ancestorToSave == null)
      {
         ancestorToSave = QPath.getCommonAncestorPath(curParent().getQPath(), node.getQPath());
      }

      NodeData parent = curParent();

      int destIndex; // index for path
      int destOrderNum; // order number

      InternalQName qname;
      if (level == 0)
      {
         qname = destNodeName;

         List<NodeData> destChilds = dataManager.getChildNodesData(parent);
         List<NodeData> srcChilds;
         NodeData srcParent;

         destIndex = 1;

         // If ordering is supported by the node
         // type of the parent node of the new location, then the
         // newly moved node is appended to the end of the child
         // node list.
         destOrderNum = destChilds.size() > 0 ? destChilds.get(destChilds.size() - 1).getOrderNumber() + 1 : 0;

         if (parent.getIdentifier().equals(node.getParentIdentifier()))
         {
            // move to same parent
            srcChilds = destChilds;
            srcParent = parent;
         }
         else
         {
            // move to another parent
            // find index on destination
            for (NodeData dchild : destChilds)
            {
               if (dchild.getQPath().getName().equals(qname))
                  destIndex++;
            }

            // for fix SNSes on source
            srcParent = (NodeData)dataManager.getItemData(node.getParentIdentifier());
            if (srcParent == null)
            {
               throw new RepositoryException("FATAL: parent Node not for " + node.getQPath().getAsString()
                  + ", parent id: " + node.getParentIdentifier());
            }

            srcChilds = dataManager.getChildNodesData(srcParent);
         }

         int srcIndex = 1;

         // Fix SNS on source
         for (int i = 0; i < srcChilds.size(); i++)
         {
            NodeData child = srcChilds.get(i);
            if (!child.getIdentifier().equals(node.getIdentifier()))
            {
               if (child.getQPath().getName().equals(qname))
               {
                  QPath siblingPath = QPath.makeChildPath(srcParent.getQPath(), child.getQPath().getName(), srcIndex);
                  TransientNodeData sibling =
                     new TransientNodeData(siblingPath, child.getIdentifier(), child.getPersistedVersion() + 1,
                        child.getPrimaryTypeName(), child.getMixinTypeNames(), child.getOrderNumber(),
                        child.getParentIdentifier(), child.getACL());

                  addStates.add(new ItemState(sibling, ItemState.UPDATED, true, ancestorToSave, false, true));

                  srcIndex++;
               }
            }
         }

         // in case of moving to the same parent destination index is calculated above
         if (srcChilds == destChilds)
         {
            destIndex = srcIndex;
         }
      }
      else
      {
         qname = node.getQPath().getName();
         destIndex = node.getQPath().getIndex();
         destOrderNum = node.getOrderNumber();
      }

      String id = keepIdentifiers ? node.getIdentifier() : IdGenerator.generate();

      QPath qpath = QPath.makeChildPath(parent.getQPath(), qname, destIndex);

      AccessControlList acl = parent.getACL();

      boolean isPrivilegeable =
         ntManager.isNodeType(Constants.EXO_PRIVILEGEABLE, node.getPrimaryTypeName(), node.getMixinTypeNames());

      boolean isOwneable =
         ntManager.isNodeType(Constants.EXO_OWNEABLE, node.getPrimaryTypeName(), node.getMixinTypeNames());

      if (isPrivilegeable || isOwneable)
      {
         List<AccessControlEntry> permissionEntries = new ArrayList<AccessControlEntry>();
         permissionEntries.addAll((isPrivilegeable ? node.getACL() : parent.getACL()).getPermissionEntries());

         String owner = isOwneable ? node.getACL().getOwner() : parent.getACL().getOwner();

         acl = new AccessControlList(owner, permissionEntries);
      }

      TransientNodeData newNode =
         new TransientNodeData(qpath, id, -1, node.getPrimaryTypeName(), node.getMixinTypeNames(), destOrderNum,
            parent.getIdentifier(), acl);

      parents.push(newNode);

      // ancestorToSave is a parent node
      // if level == 0 set internal createt as false for validating on save
      addStates.add(new ItemState(newNode, ItemState.RENAMED, level == 0, ancestorToSave, false, level == 0));
      deleteStates.add(new ItemState(node, ItemState.DELETED, level == 0, ancestorToSave, false, false));

      if (!triggerEventsForDescendents)
      {
         addStates.add(new ItemState(newNode, ItemState.PATH_CHANGED, false, ancestorToSave, false, false, node
            .getQPath()));
      }
   }

   @Override
   protected void entering(PropertyData property, int level) throws RepositoryException
   {

      InternalQName qname = property.getQPath().getName();

      List<ValueData> values;

      if (ntManager.isNodeType(Constants.MIX_REFERENCEABLE, curParent().getPrimaryTypeName(), curParent()
         .getMixinTypeNames())
         && qname.equals(Constants.JCR_UUID))
      {
         values = new ArrayList<ValueData>(1);
         values.add(new TransientValueData(curParent().getIdentifier()));
      }
      else
      {
         // we don't copy ValueDatas here as it's move (i.e. VS files will not be relocated)  
         values = property.getValues();
      }

      TransientPropertyData newProperty =
         new TransientPropertyData(QPath.makeChildPath(curParent().getQPath(), qname), keepIdentifiers ? property
            .getIdentifier() : IdGenerator.generate(), -1, property.getType(), curParent().getIdentifier(), property
            .isMultiValued(), values);

      addStates.add(new ItemState(newProperty, ItemState.RENAMED, false, ancestorToSave, false, false));

      deleteStates.add(new ItemState(property, ItemState.DELETED, false, ancestorToSave, false, false));
   }

   public List<ItemState> getAllStates()
   {
      List<ItemState> list = getItemDeletedStates(true);
      list.addAll(getItemAddStates());
      return list;
   }

   /**
    * Returns the list of item deleted states
    */
   public List<ItemState> getItemDeletedStates(boolean isInverse)
   {
      if (isInverse)
      {
         Collections.reverse(deleteStates);
      }
      return deleteStates;
   }

   /**
    * Returns the current parent node
    */
   protected NodeData curParent()
   {
      return parents.peek();
   }

   @Override
   protected void leaving(PropertyData property, int level) throws RepositoryException
   {
   }

   @Override
   protected void leaving(NodeData node, int level) throws RepositoryException
   {
      parents.pop();
   }

   /**
    * Returns the list of item add states
    */
   public List<ItemState> getItemAddStates()
   {
      return addStates;
   }
}
