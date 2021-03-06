/**
 * Copyright (c) Codice Foundation
 * 
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 * 
 **/
package org.codice.ddf.spatial.ogc.wfs.catalog.source;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.xml.namespace.QName;

import ogc.schema.opengis.wfs.v_1_0_0.DescribeFeatureTypeType;
import ogc.schema.opengis.wfs.v_1_0_0.GetCapabilitiesType;
import ogc.schema.opengis.wfs.v_1_0_0.GetFeatureType;
import ogc.schema.opengis.wfs_capabilities.v_1_0_0.WFSCapabilitiesType;

import org.apache.commons.lang.StringUtils;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.ws.commons.schema.XmlSchema;
import org.codice.ddf.spatial.ogc.catalog.common.TrustedRemoteSource;
import org.codice.ddf.spatial.ogc.wfs.catalog.common.DescribeFeatureTypeRequest;
import org.codice.ddf.spatial.ogc.wfs.catalog.common.GetCapabilitiesRequest;
import org.codice.ddf.spatial.ogc.wfs.catalog.common.Wfs;
import org.codice.ddf.spatial.ogc.wfs.catalog.common.WfsConstants;
import org.codice.ddf.spatial.ogc.wfs.catalog.common.WfsException;
import org.codice.ddf.spatial.ogc.wfs.catalog.common.WfsFeatureCollection;
import org.codice.ddf.spatial.ogc.wfs.catalog.source.reader.FeatureCollectionMessageBodyReader;
import org.codice.ddf.spatial.ogc.wfs.catalog.source.reader.XmlSchemaMessageBodyReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client to a WFS 1.0.0 Service. This class uses the {@link Wfs} interface to create a client
 * proxy from the {@link JAXRSClientFactory}.
 * 
 */
public class RemoteWfs extends TrustedRemoteSource implements Wfs {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteWfs.class);

    private Wfs wfs;

    private FeatureCollectionMessageBodyReader featureCollectionReader;

    public RemoteWfs(String wfsServerUrl, boolean disableSSLCertVerification) {
        if (StringUtils.isEmpty(wfsServerUrl)) {
            final String errMsg = "RemoteWfs(wfsUrl) was called without a WFS URL.  RemotWfs will not be able to connect.";
            LOGGER.error(errMsg);
            throw new IllegalArgumentException(errMsg);
        }

        JAXRSClientFactoryBean bean = createJAXRSClientBean(wfsServerUrl);

        bean.getInInterceptors().add(new MarkableStreamInterceptor());

        wfs = bean.create(Wfs.class);
        if (disableSSLCertVerification) {
            disableSSLCertValidation(WebClient.client(wfs));
        }

    }

    public RemoteWfs(String wfsServerUrl, String username, String password,
            boolean disableSSLCertVerification) {

        if ((StringUtils.isEmpty(wfsServerUrl)) || (StringUtils.isEmpty(username))
                || (StringUtils.isEmpty(password))) {
            final String errMsg = "RemoteWfs(wfsUrl, Username, Password) was called without a wfsUrl, username or password.  RemoteWfs will not be able to connect.";
            LOGGER.error(errMsg);
            throw new IllegalArgumentException(errMsg);
        }

        JAXRSClientFactoryBean bean = createJAXRSClientBean(wfsServerUrl);

        // Additionally, set the username and password for Basic Auth
        bean.setUsername(username);
        bean.setPassword(password);

        bean.getInInterceptors().add(new MarkableStreamInterceptor());

        wfs = bean.create(Wfs.class);
        if (disableSSLCertVerification) {
            disableSSLCertValidation(WebClient.client(wfs));
        }
    }

    private JAXRSClientFactoryBean createJAXRSClientBean(final String url) {
        JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
        bean.setServiceClass(Wfs.class);
        bean.setAddress(url);
        bean.setClassLoader(getClass().getClassLoader());
        bean.getInInterceptors().add(new LoggingInInterceptor());
        bean.getOutInterceptors().add(new LoggingOutInterceptor());

        // We need to tell the JAXBElementProvider to marshal the GetFeatureType
        // class as an element
        // because it is are missing the @XmlRootElement Annotation
        JAXBElementProvider<GetFeatureType> provider = new JAXBElementProvider<GetFeatureType>();
        Map<String, String> jaxbClassMap = new HashMap<String, String>();

        // Ensure a namespace is used when the GetFeature request is generated
        String expandedName = new QName(WfsConstants.WFS_NAMESPACE, "GetFeature").toString();
        jaxbClassMap.put(GetFeatureType.class.getName(), expandedName);
        provider.setJaxbElementClassMap(jaxbClassMap);
        provider.setMarshallAsJaxbElement(true);

        featureCollectionReader = new FeatureCollectionMessageBodyReader();

        bean.setProviders(Arrays.asList(provider, new WfsResponseExceptionMapper(),
                new XmlSchemaMessageBodyReader(), featureCollectionReader));

        return bean;
    }

    public FeatureCollectionMessageBodyReader getFeatureCollectionReader() {
        return featureCollectionReader;
    }

    @Override
    @GET
    @Consumes({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    public WFSCapabilitiesType getCapabilities(@QueryParam("")
    GetCapabilitiesRequest request) throws WfsException {
        return this.wfs.getCapabilities(request);
    }

    @Override
    @GET
    @Consumes({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    public XmlSchema describeFeatureType(@QueryParam("")
    DescribeFeatureTypeRequest request) throws WfsException {
        return this.wfs.describeFeatureType(request);
    }

    @Override
    @POST
    @Consumes({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    public WfsFeatureCollection getFeature(GetFeatureType getFeature) throws WfsException {
        return this.wfs.getFeature(getFeature);
    }

    @Override
    @POST
    @Consumes({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    public WFSCapabilitiesType getCapabilities(GetCapabilitiesType getCapabilitesRequest)
        throws WfsException {
        return this.wfs.getCapabilities(getCapabilitesRequest);
    }

    @Override
    @POST
    @Consumes({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    public XmlSchema describeFeatureType(DescribeFeatureTypeType describeFeatureRequest)
        throws WfsException {
        return this.wfs.describeFeatureType(describeFeatureRequest);
    }
}
