
/*
 * 
 */

package org.alfresco.repo.cmis.ws;

import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;

/**
 * This class was generated by Apache CXF 2.1.2
 * Mon Oct 12 11:20:37 EEST 2009
 * Generated source version: 2.1.2
 * 
 */


@WebServiceClient(name = "RepositoryService", 
                  wsdlLocation = "file:/D:/java/eclipse/workspace/WS-Binding-07b3/source/wsdl/CMISWS-Service.wsdl",
                  targetNamespace = "http://docs.oasis-open.org/ns/cmis/ws/200908/") 
public class RepositoryService extends Service {

    public final static URL WSDL_LOCATION;
    public final static QName SERVICE = new QName("http://docs.oasis-open.org/ns/cmis/ws/200908/", "RepositoryService");
    public final static QName RepositoryServicePort = new QName("http://docs.oasis-open.org/ns/cmis/ws/200908/", "RepositoryServicePort");
    static {
        URL url = null;
        try {
            url = new URL("file:/D:/java/eclipse/workspace/WS-Binding-07b3/source/wsdl/CMISWS-Service.wsdl");
        } catch (MalformedURLException e) {
            System.err.println("Can not initialize the default wsdl from file:/D:/java/eclipse/workspace/WS-Binding-07b3/source/wsdl/CMISWS-Service.wsdl");
            // e.printStackTrace();
        }
        WSDL_LOCATION = url;
    }

    public RepositoryService(URL wsdlLocation) {
        super(wsdlLocation, SERVICE);
    }

    public RepositoryService(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public RepositoryService() {
        super(WSDL_LOCATION, SERVICE);
    }

    /**
     * 
     * @return
     *     returns RepositoryServicePort
     */
    @WebEndpoint(name = "RepositoryServicePort")
    public RepositoryServicePort getRepositoryServicePort() {
        return super.getPort(RepositoryServicePort, RepositoryServicePort.class);
    }

    /**
     * 
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns RepositoryServicePort
     */
    @WebEndpoint(name = "RepositoryServicePort")
    public RepositoryServicePort getRepositoryServicePort(WebServiceFeature... features) {
        return super.getPort(RepositoryServicePort, RepositoryServicePort.class, features);
    }

}
