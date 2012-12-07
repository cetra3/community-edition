/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.repo.transfer.fsr;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.repo.transfer.TransferCommons;
import org.alfresco.repo.transfer.TransferProcessingException;
import org.alfresco.repo.transfer.manifest.TransferManifestDeletedNode;
import org.alfresco.repo.transfer.manifest.TransferManifestNormalNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.transfer.TransferException;
import org.alfresco.service.cmr.transfer.TransferReceiver;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FileTransferSecondaryManifestProcessor extends AbstractFileManifestProcessorBase
{
    private final static Log log = LogFactory.getLog(FileTransferSecondaryManifestProcessor.class);

    protected List<TransferManifestNormalNode> waitingNodeList;

    protected Set<String> receivedNodes;
    
    private TransactionService transactionService;
    private DeletedNodeProcessor deletedNodeProcessor = new DeletedNodeProcessor(); 
    private NormalNodeProcessor normalNodeProcessor = new NormalNodeProcessor(); 

    public FileTransferSecondaryManifestProcessor(TransferReceiver receiver, String transferId, TransactionService transactionService)
    {
        super(receiver, transferId);
        this.transactionService = transactionService;
    }

    @Override
    protected void startManifest()
    {
        if (log.isDebugEnabled())
        {
            log.debug("Processing manifest started");
        }

        waitingNodeList = new ArrayList<TransferManifestNormalNode>();
        // reset the set of received nodes.
        receivedNodes = new HashSet<String>(179);
    }

    @Override
    protected void endManifest()
    {
        if (log.isDebugEnabled())
        {
            log.debug("End manifest!");
        }
        // delete TEMP_VIRT_ROOT is exist
        File tvr = new File(fTReceiver.getDefaultReceivingroot() + getTemporaryFolderPath());
        if (tvr.exists())
        {
            tvr.delete();
        }

        // delete staging folder if exist
        File stagingFolder = fTReceiver.getStagingFolder(this.fTransferId);
        if (stagingFolder.exists())
        {
            stagingFolder.delete();
        }

        // if isSyncMode = true the do the implicit delete
        if (this.isSync)
        {
            SortedSet<String> nodesToDeleteInSyncMode = fTReceiver.getListOfDescendentsForSyncMode();
            if (log.isDebugEnabled())
            {
                log.debug("nodesToDeleteInSyncMode...");
                dumpSet(nodesToDeleteInSyncMode);
                log.debug("this.receivedNodes...");
                dumpSet(this.receivedNodes);
            }

            nodesToDeleteInSyncMode.removeAll(this.receivedNodes);

            if (log.isDebugEnabled())
            {
                log.debug("nodesToDeleteInSyncMode after remove:");
                dumpSet(nodesToDeleteInSyncMode);
            }

            // delete all the remaining nodes depth first
            while (!nodesToDeleteInSyncMode.isEmpty())
            {
                purgeDepthFirst(nodesToDeleteInSyncMode, nodesToDeleteInSyncMode.first());
            }
        }

        // Give the nodes their final name
        List<FileTransferNodeRenameEntity> renameRecords = 
            fTReceiver.findFileTransferNodeRenameEntityByTransferId(fTransferId);
        for (final FileTransferNodeRenameEntity fileTransferNodeRenameEntity : renameRecords)
        {
            transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Void>()
            {
                @Override
                public Void execute() throws Throwable
                {
                    String newName = fileTransferNodeRenameEntity.getNewName();
                    FileTransferInfoEntity fileTransferInfoEntiy = fTReceiver
                            .findFileTransferInfoByNodeRef(fileTransferNodeRenameEntity.getRenamedNodeRef());
                    String oldName = fileTransferInfoEntiy.getContentName();
                    fileTransferInfoEntiy.setContentName(newName);
                    // update DB
                    fTReceiver.updateFileTransferInfoByNodeRef(fileTransferInfoEntiy);
                    // update the name on file system using the old name
                    String nodePath = fileTransferInfoEntiy.getPath();
                    moveFileOrFolderOnFileSytem(nodePath + "/", oldName, nodePath + "/", newName);
                    adjustPathInSubtreeInDB(fileTransferInfoEntiy, nodePath + "/" + newName + "/");
                    fTReceiver.deleteNodeRenameByTransferIdAndNodeRef(fTransferId, fileTransferNodeRenameEntity.getRenamedNodeRef());
                    return null;
                }
            }, false, true);
        }
    }

    protected void dumpSet(Set<String> set)
    {
        Iterator<String> i = set.iterator();
        while (i.hasNext())
        {
            String s = i.next();
            log.debug(":" + s);
        }
    }

    protected void purgeDepthFirst(final Set<String> nodesToDeleteInSyncMode, final String nodeRef)
    {

        // get all children of nodeToModify
        List<FileTransferInfoEntity> childrenList = fTReceiver.findFileTransferInfoByParentNodeRef(nodeRef.toString());
        // iterate on children
        for (FileTransferInfoEntity curChild : childrenList)
        {
            purgeDepthFirst(nodesToDeleteInSyncMode, curChild.getNodeRef());
        }

        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Void>()
        {
            @Override
            public Void execute() throws Throwable
            {
                FileTransferInfoEntity deletedNode = fTReceiver.findFileTransferInfoByNodeRef(nodeRef);
                String pathFileOrFolderToBeDeleted = fTReceiver.getDefaultReceivingroot() + deletedNode.getPath()
                        + deletedNode.getContentName();
                File fileOrFolderToBeDeleted = new File(pathFileOrFolderToBeDeleted);

                try
                {
//                    fTReceiver.getProgressMonitor().logDeleted(fTransferId, nodeRef, nodeRef, 
//                            pathFileOrFolderToBeDeleted);
                    fileOrFolderToBeDeleted.delete();
                    fTReceiver.deleteNodeByNodeRef(nodeRef);
                    nodesToDeleteInSyncMode.remove(nodeRef);
                }
                catch (Exception e)
                {
                    log.error("Failed to delete :" + pathFileOrFolderToBeDeleted, e);
                    throw new TransferException("Failed to delete node:", e);
                }
                return null;
            }
        }, false, true);
    }

    @Override
    protected void processNode(TransferManifestNormalNode node) throws TransferProcessingException
    {
        if (log.isDebugEnabled())
        {
            log.debug("Starting processing node" + node.toString());
            log.debug("Starting processing node,nodeRef:" + node.getNodeRef());
            log.debug("Starting processing node,name:" + (String) node.getProperties().get(ContentModel.PROP_NAME));
            log.debug("Starting processing node,content Url:" + getContentUrl(node));
            log.debug("Starting processing node,content properties:" + node.getProperties());
        }

        //Skip over any nodes that are not parented with a cm:contains association or 
        //are not content or folders
        if (!ContentModel.ASSOC_CONTAINS.equals(node.getPrimaryParentAssoc().getTypeQName()) ||
                !(ContentModel.TYPE_FOLDER.equals(node.getType()) ||
                        ContentModel.TYPE_CONTENT.equals(node.getType())))
        {
            if (log.isDebugEnabled())
            {
                log.debug("Skipping node due to either: not content; not folder; or not cm:contains");
            }
            return;
        }

        normalNodeProcessor.setNode(node);
        transactionService.getRetryingTransactionHelper().doInTransaction(normalNodeProcessor, false, true);
    }

    @Override
    protected void processNode(TransferManifestDeletedNode node) throws TransferProcessingException
    {
        deletedNodeProcessor.setNode(node);
        transactionService.getRetryingTransactionHelper().doInTransaction(deletedNodeProcessor, false, true);
    }
    
    private class DeletedNodeProcessor implements RetryingTransactionCallback<Void>
    {
        private TransferManifestDeletedNode node;
        
        public void setNode(TransferManifestDeletedNode node)
        {
            this.node = node;
        }

        @Override
        public Void execute() throws Throwable
        {
            FileTransferInfoEntity deletedNode = fTReceiver.findFileTransferInfoByNodeRef(
                    node.getNodeRef().toString());

            // If null just log and ignore
            // delete on FS
            String pathFileOrFolderToBeDeleted = fTReceiver.getDefaultReceivingroot() + deletedNode.getPath()
                    + deletedNode.getContentName();

            File fileOrFolderToBeDeleted = new File(pathFileOrFolderToBeDeleted);

            try
            {
                NodeRef nodeRef = node.getNodeRef();
                fTReceiver.getProgressMonitor().logDeleted(fTransferId, nodeRef, nodeRef, 
                        pathFileOrFolderToBeDeleted);
                if (fileOrFolderToBeDeleted.isDirectory())
                {
                    FileUtils.deleteDirectory(fileOrFolderToBeDeleted);
                    fileOrFolderToBeDeleted.delete();
                }
                else
                {
                    fileOrFolderToBeDeleted.delete();
                }
                recursiveDeleteInDB(nodeRef.toString());
            }
            catch (Exception e)
            {
                log.error("Failed to delete :" + pathFileOrFolderToBeDeleted, e);
                throw new TransferException("Failed to delete node:", e);

            }
            return null;
        }
        
        private void recursiveDeleteInDB(final String nodeRef)
        {
            fTReceiver.deleteNodeByNodeRef(nodeRef.toString());
            // get all children of nodeToModify
            List<FileTransferInfoEntity> childrenList = fTReceiver.findFileTransferInfoByParentNodeRef(nodeRef.toString());
            // iterate on children
            for (FileTransferInfoEntity curChild : childrenList)
            {
                recursiveDeleteInDB(curChild.getNodeRef().toString());
            }
        }
    }
    
    private class NormalNodeProcessor implements RetryingTransactionCallback<Void>
    {
        private TransferManifestNormalNode node;
        
        public void setNode(TransferManifestNormalNode node)
        {
            this.node = node;
        }

        @Override
        public Void execute() throws Throwable
        {
            String nodeRef = node.getNodeRef().toString();
            // In sync mode, convention is that if a node is not received then it is an implicit delete
            if (isSync)
            {
                receivedNodes.add(nodeRef);
            }

            if (nodeRef.equals(fTReceiver.getTransferRootNode()))
            {
                if (log.isDebugEnabled())
                {
                    log.debug("Skipping over the root node");
                }
                return null;
            }
            
            String name = (String) node.getProperties().get(ContentModel.PROP_NAME);

            // not a new node, it can be a move of a folder or a file
            // it can be a content modification or a rename of folder or file
            // check if we receive a file or a folder
            QName nodeType = node.getAncestorType();
            FileTransferInfoEntity nodeToModify = fTReceiver.findFileTransferInfoByNodeRef(node.getNodeRef().toString());
            Boolean isFolder = ContentModel.TYPE_FOLDER.equals(nodeType);
            if (!isFolder)
            {
                if (log.isDebugEnabled())
                {
                    log.debug("This is a content node");
                }
                // check if content has changed
                boolean isContentModified = fTReceiver.isContentNewOrModified(node.getNodeRef().toString(), 
                        getContentUrl(node));

                if (isContentModified)
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("Content URL was modified:" + name);
                    }
                    File receivedFile = new File(fTReceiver.getDefaultReceivingroot() + nodeToModify.getPath()
                            + nodeToModify.getContentName());
                    String contentKey = TransferCommons.URLToPartName(getContentUrl(node));
                    File receivedContent = fTReceiver.getContents().get(contentKey);
                    if (receivedContent == null)
                    {
                        if (log.isDebugEnabled())
                        {
                            log.debug("Error content not there:" + fTReceiver.getDefaultReceivingroot()
                                    + nodeToModify.getPath() + nodeToModify.getContentName());
                            log.debug("Key is:" + contentKey);
                        }
                        throw new TransferException("MSG_ERROR_CONTENT_NOT_THERE:" + contentKey);
                    }

                    putFileContent(receivedFile, receivedContent);
                    // update DB
                    nodeToModify.setContentUrl(getContentUrl(node));
                    fTReceiver.updateFileTransferInfoByNodeRef(nodeToModify);
                }

                // Check if the node was moved
                String parentOfNode = node.getPrimaryParentAssoc().getParentRef().toString();
                if (!parentOfNode.equals(nodeToModify.getParent()))
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("Node is moved, nodeRef:" + nodeToModify.getNodeRef());
                        log.debug("Node is moved, node Id:" + nodeToModify.getId());
                        log.debug("Node is moved, old parent :" + nodeToModify.getParent());
                        log.debug("Node is moved, new parent :" + parentOfNode);
                        log.debug("Node is moved, old path :" + nodeToModify.getPath());
                    }
                    // retrieve the new parent
                    FileTransferInfoEntity newParentEntity = fTReceiver.findFileTransferInfoByNodeRef(parentOfNode);

                    // adjust the parent node
                    nodeToModify.setParent(parentOfNode);
                    if (newParentEntity != null)
                    {
                        // adjust the path because parent already exist
                        String newPath = newParentEntity.getPath() + newParentEntity.getContentName() + "/";
                        if (log.isDebugEnabled())
                        {
                            log.debug("Node is moved, new path :" + newPath);
                        }
                        String oldPath = nodeToModify.getPath();
                        nodeToModify.setPath(newPath);
                        moveFileOrFolderOnFileSytem(oldPath, nodeToModify.getContentName(), newPath, nodeToModify
                                .getContentName());
                    }
                    else
                    {
                        // it could be a node directly under root
                        // should be moved under the root?
                        boolean isNodeUnderRoot = parentOfNode.equals(fTReceiver.getTransferRootNode());
                        name = nodeToModify.getContentName();
                        if (isNodeUnderRoot)
                        {
                            // adjust the path
                            String newPath = "/";
                            String oldPath = nodeToModify.getPath();
                            nodeToModify.setPath(newPath);
                            moveFileOrFolderOnFileSytem(oldPath, name, newPath, name);
                            // use the new name because maybe it was renamed just before
                            adjustPathInSubtreeInDB(nodeToModify, newPath + name + "/");
                        }
                    }
                    fTReceiver.updateFileTransferInfoByNodeRef(nodeToModify);
                }

            }
            else
            {
                // this is folder modified
                if (log.isDebugEnabled())
                {
                    log.debug("This is folder:" + node.toString());
                }
                // Maybe parent was also modified then we have to do the move on FS and update the
                // full subtree in the DB
                String parentOfNode = node.getPrimaryParentAssoc().getParentRef().toString();
                if (!parentOfNode.equals(nodeToModify.getParent()))
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("Node is moved, nodeFef:" + nodeToModify.getNodeRef());
                        log.debug("Node is moved, node Id:" + nodeToModify.getId());
                        log.debug("Node is moved, old parent :" + nodeToModify.getParent());
                        log.debug("Node is moved, new parent :" + parentOfNode);
                        log.debug("Node is moved, old path :" + nodeToModify.getPath());
                    }
                    // if the new parent is a descendant of the new received node then
                    // it can not be treated now,keep it for later
                    if (isDescendant(parentOfNode, node.getNodeRef().toString()))
                    {
                        // keep the untreated node in a list to handle it later
                        waitingNodeList.add(node);

                    }
                    else
                    {
                        // retrieve the new parent
                        FileTransferInfoEntity newParentEntity = fTReceiver.findFileTransferInfoByNodeRef(parentOfNode);
                        // adjust the parent node
                        nodeToModify.setParent(parentOfNode);
                        if (newParentEntity != null)
                        {
                            // adjust the path
                            String newPath = newParentEntity.getPath() + newParentEntity.getContentName() + "/";
                            String oldPath = nodeToModify.getPath();
                            nodeToModify.setPath(newPath);
                            moveFileOrFolderOnFileSytem(oldPath, nodeToModify.getContentName(), newPath, nodeToModify
                                    .getContentName());
                            // use the new name because maybe it was renamed just before
                            adjustPathInSubtreeInDB(nodeToModify, newPath + nodeToModify.getContentName() + "/");
                        }
                        else
                        {
                            /** **************************************************************************** */
                            /* Some test here to handle the "root" case if parent does not exist in the DB */
                            /** **************************************************************************** */
                            boolean isNodeUnderRoot = parentOfNode.equals(fTReceiver
                                    .getTransferRootNode());
                            if (log.isDebugEnabled())
                            {
                                log.debug("Node is moved, nodeFef:" + nodeToModify.getNodeRef());
                            }
                            if (isNodeUnderRoot)
                            {
                                // adjust the path
                                String newPath = "/";
                                String oldPath = nodeToModify.getPath();
                                nodeToModify.setPath(newPath);
                                moveFileOrFolderOnFileSytem(oldPath, nodeToModify.getContentName(), newPath, nodeToModify
                                        .getContentName());
                                // use the new name because maybe it was renamed just before
                                adjustPathInSubtreeInDB(nodeToModify, newPath + nodeToModify.getContentName() + "/");
                            }
                            else
                            {
                                // throw new TransferException("MSG_NO_PARENT_FOUND:" + nodeToModify);
                            }
                            // throw new TransferException("MSG_NO_PARENT_FOUND:" + nodeToModify);
                        }
                        /** **************************************************************************** */
                        /* Some test here to handle the "root" case if parent does not exist in the DB */
                        /** **************************************************************************** */
                        fTReceiver.updateFileTransferInfoByNodeRef(nodeToModify);
                        // check if some waiting nodes can be handle now
                        handleWaitingNodes();
                    }
                }
            }
            return null;
        }
        
        protected void handleWaitingNodes()
        {
            while (true)
            {
                List<TransferManifestNormalNode> toBeRemoved = new ArrayList<TransferManifestNormalNode>();
                // check the waiting nodes
                for (TransferManifestNormalNode node : waitingNodeList)
                {
                    String parentparentOfNode = node.getPrimaryParentAssoc().getParentRef().toString();

                    if (!isDescendant(parentparentOfNode, node.getNodeRef().toString()))
                    {
                        FileTransferInfoEntity nodeToModify = fTReceiver.findFileTransferInfoByNodeRef(node.getNodeRef()
                                .toString());
                        // now this node can be handled
                        // retrieve the new parent
                        FileTransferInfoEntity newParentEntity = fTReceiver
                                .findFileTransferInfoByNodeRef(parentparentOfNode);
                        // adjust the parent node
                        nodeToModify.setParent(parentparentOfNode);
                        if (newParentEntity != null)
                        {
                            String name = nodeToModify.getContentName();
                            // adjust the path
                            String newPath = newParentEntity.getPath() + newParentEntity.getContentName() + "/";
                            String oldPath = nodeToModify.getPath();
                            nodeToModify.setPath(newPath);
                            moveFileOrFolderOnFileSytem(oldPath, name, newPath, name);
                            // use the new name because maybe it was renamed just before
                            adjustPathInSubtreeInDB(nodeToModify, newPath + name + "/");
                            fTReceiver.updateFileTransferInfoByNodeRef(nodeToModify);
                            // remove current node from the list
                            toBeRemoved.add(node);
                        }
                        else
                        {
                            // should be moved under the root?
                            boolean isNodeUnderRoot = parentparentOfNode.equals(fTReceiver
                                    .getTransferRootNode());
                            if (log.isDebugEnabled())
                            {
                                log.debug("Node is moved, nodeFef:" + nodeToModify.getNodeRef());
                            }
                            String name = nodeToModify.getContentName();
                            if (isNodeUnderRoot)
                            {
                                // adjust the path
                                String newPath = "/";
                                String oldPath = nodeToModify.getPath();
                                nodeToModify.setPath(newPath);
                                moveFileOrFolderOnFileSytem(oldPath, name, newPath, name);
                                // use the new name because maybe it was renamed just before
                                adjustPathInSubtreeInDB(nodeToModify, newPath + name + "/");
                                fTReceiver.updateFileTransferInfoByNodeRef(nodeToModify);
                            }
                            else
                            {
                                throw new TransferException("MSG_NO_PARENT_FOUND:" + nodeToModify);
                            }

                        }
                    }
                }
                if (toBeRemoved.isEmpty())
                {
                    break;
                }
                waitingNodeList.removeAll(toBeRemoved);
            }
        }

        /**
         * Check if newParentNode is a descendant of the node that has to be moved.
         *
         * @param newParentOfNode
         * @param nodeToBeMoved
         * @return
         */
        private boolean isDescendant(String newParentOfNode, String nodeToBeMoved)
        {
            // going from the new parent up to the root and check if we encounter the nodeToBeMoved
            FileTransferInfoEntity curNode = null;
            while (true)
            {
                curNode = fTReceiver.findFileTransferInfoByNodeRef(newParentOfNode);
                if (curNode == null)
                    return false;
                newParentOfNode = curNode.getParent();
                if (nodeToBeMoved.equals(newParentOfNode))
                {
                    return true;
                }
            }
        }

    }
}
