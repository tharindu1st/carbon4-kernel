/**
 *  Copyright (c) 2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.carbon.ndatasource.core.utils;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.util.SecurityManager;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.core.util.CryptoException;
import org.wso2.carbon.core.util.CryptoUtil;
import org.wso2.carbon.ndatasource.common.DataSourceConstants;
import org.wso2.carbon.ndatasource.common.DataSourceException;
import org.wso2.carbon.ndatasource.core.DataSourceMetaInfo;
import org.wso2.carbon.ndatasource.core.internal.DataSourceServiceComponent;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;
import org.wso2.securevault.commons.MiscellaneousUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Iterator;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Data Sources utility class.
 */
public class DataSourceUtils {

	private static Log log = LogFactory.getLog(DataSourceUtils.class);

	private static SecretResolver secretResolver;
    private static final String XML_DECLARATION = "xml-declaration";
	private static final int ENTITY_EXPANSION_LIMIT = 0;
	private static final DocumentBuilderFactory dbf;

	static {
		dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		dbf.setXIncludeAware(false);
		dbf.setExpandEntityReferences(false);
		try {
			dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
			dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
			dbf.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.LOAD_EXTERNAL_DTD_FEATURE, false);
		} catch (ParserConfigurationException e) {
			log.error("Failed to load XML Processor Feature " + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE
					+ " or " + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE
					+ " or " + Constants.LOAD_EXTERNAL_DTD_FEATURE);
		}

		SecurityManager securityManager = new SecurityManager();
		securityManager.setEntityExpansionLimit(ENTITY_EXPANSION_LIMIT);
		dbf.setAttribute(Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY, securityManager);
	}

	private static ThreadLocal<String> dataSourceId = new ThreadLocal<String>() {
        protected synchronized String initialValue() {
            return null;
        }
    };

    public static void setCurrentDataSourceId(String dsId) {
    	dataSourceId.set(dsId);
    }

    public static String getCurrentDataSourceId() {
    	return dataSourceId.get();
    }

	public static Registry getConfRegistryForTenant(int tenantId) throws DataSourceException {
		try {
			/* be super tenant to retrieve the registry of a given tenant id */
			PrivilegedCarbonContext.startTenantFlow();
			PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(
					MultitenantConstants.SUPER_TENANT_ID);
			PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(
					MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
			return DataSourceServiceComponent.getRegistryService().getConfigSystemRegistry(
					tenantId);
		} catch (RegistryException e) {
			throw new DataSourceException("Error in retrieving conf registry instance: " +
		            e.getMessage(), e);
		} finally {
			/* go out of being super tenant */
			PrivilegedCarbonContext.endTenantFlow();
		}
	}

	public static Registry getGovRegistryForTenant(int tenantId) throws DataSourceException {
		try {
			/* be super tenant to retrieve the registry of a given tenant id */
			PrivilegedCarbonContext.startTenantFlow();
			PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(
                    MultitenantConstants.SUPER_TENANT_ID);
			return DataSourceServiceComponent.getRegistryService().getGovernanceSystemRegistry(
                    tenantId);
		} catch (RegistryException e) {
			throw new DataSourceException("Error in retrieving gov registry instance: " +
		            e.getMessage(), e);
		} finally {
			/* go out of being super tenant */
			PrivilegedCarbonContext.endTenantFlow();
		}
	}

	public static boolean nullAllowEquals(Object lhs, Object rhs) {
		if (lhs == null && rhs == null) {
			return true;
		}
		if ((lhs == null && rhs != null) || (lhs != null && rhs == null)) {
			return false;
		}
		return lhs.equals(rhs);
	}

	public static String elementToString(Element element) {
		try {
			if (element == null) {
                                /* return an empty string because, the other way around works the same,
                                where if we give a empty string as the XML, we get a null element
                                from "stringToElement" */
				return "";
			}
            Document document = element.getOwnerDocument();
            DOMImplementationLS domImplLS = (DOMImplementationLS) document.getImplementation();
            LSSerializer serializer = domImplLS.createLSSerializer();
            //by default its true, so set it to false to get String without xml-declaration
            serializer.getDomConfig().setParameter(XML_DECLARATION, false);
            return serializer.writeToString(element);
		} catch (Exception e) {
			log.error("Error while convering element to string: " + e.getMessage(), e);
			return null;
		}
	}

	public static Element stringToElement(String xml) {
		if (xml == null || xml.trim().length() == 0) {
			return null;
		}
		try {
            DocumentBuilder db = dbf.newDocumentBuilder();
		    return db.parse(new ByteArrayInputStream(xml.getBytes())).getDocumentElement();
		} catch (Exception e) {
			log.error("Error while converting string to element: " + e.getMessage(), e);
			return null;
		}
	}

	private static synchronized String loadFromSecureVault(String alias) {
		if (secretResolver == null) {
		    secretResolver = SecretResolverFactory.create((OMElement) null, false);
		    secretResolver.init(DataSourceServiceComponent.
		    		getSecretCallbackHandlerService().getSecretCallbackHandler());
		}
		return secretResolver.resolve(alias);
	}

    private static void secureLoadElement(Element element, boolean checkSecureVault)
			throws CryptoException {
		if (checkSecureVault) {
			Attr secureAttr = element.getAttributeNodeNS(DataSourceConstants.SECURE_VAULT_NS,
					DataSourceConstants.SECRET_ALIAS_ATTR_NAME);
			if (secureAttr != null) {
				element.setTextContent(loadFromSecureVault(secureAttr.getValue()));
                element.removeAttributeNode(secureAttr);
			}
		} else {
		    String encryptedStr = element.getAttribute(DataSourceConstants.ENCRYPTED_ATTR_NAME);
		    if (encryptedStr != null) {
			    boolean encrypted = Boolean.parseBoolean(encryptedStr);
			    if (encrypted) {
				    element.setTextContent(new String(CryptoUtil.getDefaultCryptoUtil(
				    		DataSourceServiceComponent.getServerConfigurationService(),
				    		DataSourceServiceComponent.getRegistryService()).
				    		base64DecodeAndDecrypt(element.getTextContent())));
			    }
		    }
		}
		NodeList childNodes = element.getChildNodes();
		int count = childNodes.getLength();
		Node tmpNode;
		for (int i = 0; i < count; i++) {
			tmpNode = childNodes.item(i);
			if (tmpNode instanceof Element) {
				secureLoadElement((Element) tmpNode, checkSecureVault);
			}
		}
	}

	private static void secureLoadOMElement(OMElement element, boolean checkSecureVault)
			throws CryptoException {

		if (checkSecureVault) {
			String alias = MiscellaneousUtil.getProtectedToken(element.getText());
			if (alias != null && !alias.isEmpty()) {
				element.setText(loadFromSecureVault(alias));
			} else {
				OMAttribute secureAttr = element.getAttribute(new QName(DataSourceConstants.SECURE_VAULT_NS,
						DataSourceConstants.SECRET_ALIAS_ATTR_NAME));
				if (secureAttr != null) {
					element.setText(loadFromSecureVault(secureAttr.getAttributeValue()));
					element.removeAttribute(secureAttr);
				}
			}
		} else {
			String encryptedStr = element.getAttributeValue(new QName(DataSourceConstants.ENCRYPTED_ATTR_NAME));
			if (encryptedStr != null) {
				boolean encrypted = Boolean.parseBoolean(encryptedStr);
				if (encrypted) {
					element.setText(new String(CryptoUtil.getDefaultCryptoUtil(
							DataSourceServiceComponent.getServerConfigurationService(),
							DataSourceServiceComponent.getRegistryService()).
							base64DecodeAndDecrypt(element.getText())));
				}
			}
		}
		Iterator<OMElement> childNodes = element.getChildElements();
		while (childNodes.hasNext()) {
			OMElement tmpNode = childNodes.next();
			secureLoadOMElement(tmpNode, checkSecureVault);
		}
	}

	public static void secureSaveElement(Element element) throws CryptoException {
		String encryptedStr = element.getAttribute(DataSourceConstants.ENCRYPTED_ATTR_NAME);
		if (encryptedStr != null) {
		    boolean encrypted = Boolean.parseBoolean(encryptedStr);
		    if (encrypted) {
			    element.setTextContent(CryptoUtil.getDefaultCryptoUtil(
			    		DataSourceServiceComponent.getServerConfigurationService(),
			    		DataSourceServiceComponent.getRegistryService()).
			    		encryptAndBase64Encode(element.getTextContent().getBytes()));
		    }
		}
		NodeList childNodes = element.getChildNodes();
		int count = childNodes.getLength();
		Node tmpNode;
		for (int i = 0; i < count; i++) {
			tmpNode = childNodes.item(i);
			if (tmpNode instanceof Element) {
				secureSaveElement((Element) tmpNode);
			}
		}
	}

	public static void secureResolveDocument(Document doc, boolean checkSecureVault)
            throws DataSourceException {
        Element element = doc.getDocumentElement();
		if (element != null) {
			try {
				secureLoadElement(element, checkSecureVault);
			} catch (CryptoException e) {
				throw new DataSourceException("Error in secure load of data source meta info: " +
			            e.getMessage(), e);
			}
		}
    }

    public static Document convertToDocument(File file) throws DataSourceException {
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(file);
        } catch (Exception e) {
            throw new DataSourceException("Error in creating an XML document from file: " +
                    e.getMessage(), e);
        }
    }

    public static Document convertToDocument(InputStream in) throws DataSourceException {
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(in);
        } catch (Exception e) {
            throw new DataSourceException("Error in creating an XML document from stream: " +
                    e.getMessage(), e);
        }
    }

    public static void secureResolveOMElement(OMElement doc, boolean checkSecureVault)
            throws DataSourceException {

        if (doc != null) {
            try {
                secretResolver = SecretResolverFactory.create(doc, true);
                secureLoadOMElement(doc, checkSecureVault);
            } catch (CryptoException e) {
                throw new DataSourceException("Error in secure load of data source meta info: " +
                        e.getMessage(), e);
            }
        }
    }

    public static OMElement convertToOMElement(File file) throws DataSourceException {

        try {
            OMElement documentElement = new StAXOMBuilder(new FileInputStream(file)).getDocumentElement();
            return documentElement;
        } catch (Exception e) {
            throw new DataSourceException("Error in creating an XML document from file: " +
                    e.getMessage(), e);
        }
    }

    public static OMElement convertToOMElement(InputStream in) throws DataSourceException {

        try {
            OMElement documentElement = new StAXOMBuilder(in).getDocumentElement();
            return documentElement;
        } catch (Exception e) {
            throw new DataSourceException("Error in creating an XML document from stream: " +
                    e.getMessage(), e);
        }
    }

    public static InputStream elementToInputStream(Element element) {
		try {
			if (element == null) {
				return null;
			}
            String xmlString = elementToString(element);
            InputStream stream = new ByteArrayInputStream(xmlString.getBytes());
            return stream;
		} catch (Exception e) {
			log.error("Error while convering element to InputStream: " + e.getMessage(), e);
			return null;
		}
	}

    public static Element convertDataSourceMetaInfoToElement(DataSourceMetaInfo dsmInfo,
    		Marshaller dsmMarshaller) throws DataSourceException{
    	Element element;
		try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.newDocument();
			dsmMarshaller.marshal(dsmInfo, document);
			element = document.getDocumentElement();
		} catch (Exception e) {
			throw new DataSourceException("Error in creating an XML document from stream: " +
                    e.getMessage(), e);
		}
		return element;
    }
}
