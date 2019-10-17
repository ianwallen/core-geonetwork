/*
 * =============================================================================
 * ===	Copyright (C) 2001-2016 Food and Agriculture Organization of the
 * ===	United Nations (FAO-UN), United Nations World Food Programme (WFP)
 * ===	and United Nations Environment Programme (UNEP)
 * ===
 * ===	This program is free software; you can redistribute it and/or modify
 * ===	it under the terms of the GNU General Public License as published by
 * ===	the Free Software Foundation; either version 2 of the License, or (at
 * ===	your option) any later version.
 * ===
 * ===	This program is distributed in the hope that it will be useful, but
 * ===	WITHOUT ANY WARRANTY; without even the implied warranty of
 * ===	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * ===	General Public License for more details.
 * ===
 * ===	You should have received a copy of the GNU General Public License
 * ===	along with this program; if not, write to the Free Software
 * ===	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
 * ===
 * ===	Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
 * ===	Rome - Italy. email: geonetwork@osgeo.org
 * ==============================================================================
 */

package org.fao.geonet.api.records.attachments;


import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.fao.geonet.domain.MetadataResource;
import org.fao.geonet.domain.MetadataResourceVisibility;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;

import java.io.IOException;
import java.io.InputStream;

/**
 * Metadata resource stored in the cloud blob environment.
 *
 * Created by Ian on 04/10/19.
 */
public class JCloudFilesystemStoreResource implements MetadataResource {
    private final String id;
    private final String url;
    private final MetadataResourceVisibility metadataResourceVisibility;
    private double size = -1;
    private String contentType;
    private String dataDirectoryRelativePath;

    private BlobStore blobStore;
    private BlobStoreContext blobStoreContext;
    private String containerName;

    public JCloudFilesystemStoreResource(String id,
                                         String baseUrl,
                                         MetadataResourceVisibility metadataResourceVisibility,
                                         double size,
                                         String contentType,
                                         String dataDirectoryRelativePath) {
        this.id = id;
        this.url = baseUrl + id;
        this.metadataResourceVisibility = metadataResourceVisibility;
        this.size = Double.isNaN(size) ? -1 : size;
        this.contentType = contentType;
        this.dataDirectoryRelativePath = dataDirectoryRelativePath;

        JCloudConnection jCloudConnection = JCloudConnection.getNewJcloudConnection();
        // Get a context with amazon that offers the portable BlobStore api
        blobStoreContext = jCloudConnection.getBlobStoreContext();
        // Access the BlobStore
        blobStore = blobStoreContext.getBlobStore();
        containerName= jCloudConnection.getContainerName();
        if(!blobStore.containerExists(containerName)) {
            blobStore.createContainerInLocation(null, containerName);
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getType() {
        return metadataResourceVisibility.toString();
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer(this.getClass().getSimpleName());
        sb.append("\n");
        sb.append("Id: ").append(id).append("\n");
        sb.append("URL: ").append(url).append("\n");
        sb.append("Type: ").append(metadataResourceVisibility).append("\n");
        sb.append("Size: ").append(size).append("\n");
        sb.append("ContentType: ").append(contentType).append("\n");
        sb.append("RelativePath: ").append(dataDirectoryRelativePath).append("\n");
        return sb.toString();
    }

    @Override
    public String getFileName() {
        return FilenameUtils.getName(id);
    }

    @Override
    public byte[] getBytes() throws IOException {

        if (blobStore.blobExists(containerName, dataDirectoryRelativePath)) {
            InputStream inputStream = blobStore.getBlob(containerName, dataDirectoryRelativePath).getPayload().openStream();
            byte[] bytes = IOUtils.toByteArray(inputStream);
            inputStream.close();
            return bytes;
        } else {
            throw new IOException("File not found");
        }
   }

    @Override
    public String getContentType() throws IOException {
        return this.contentType;
    }

    @Override
    public String getDataDirectoryRelativePath() {
        return dataDirectoryRelativePath;
    }
}
