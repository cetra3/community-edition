package org.alfresco.repo.googledocs;

import java.io.InputStream;

import org.alfresco.service.cmr.repository.NodeRef;

/**
 * Google docs integration service interface
 */
public interface GoogleDocsService 
{
    /**
     * Create a google doc from a given node.  The content of the node will be used 
     * as a basis of the associated google doc.  If the node has no content a new, empty google
     * doc of the correct type will be created.
     * 
     * The permission context provides information about how google sharing permissions should be 
     * set on the created google doc.
     * 
     * @param nodeRef               node reference
     * @param permissionContext     permission context
     */
    void createGoogleDoc(NodeRef nodeRef, GoogleDocsPermissionContext permissionContext);
    
    /**
     * Deletes the google resource associated with the node reference.  This could be a folder or 
     * document.
     *  
     * @param nodeRef   node reference
     */
    void deleteGoogleResource(NodeRef nodeRef);

    /**
     * Gets the content as an input stream of google doc associated with the given node.  The 
     * node must have the google resource aspect and the associated resource should not be a 
     * folder.
     * 
     * @param nodeRef        node reference
     * @return InputStream   the content of the associated google doc
     */
    InputStream getGoogleDocContent(NodeRef nodeRef);

}