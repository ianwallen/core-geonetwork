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

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;

/**
 * Manage jcloud connections
 * This is just for proof of concept - this should be done using properties.
 */
public class JCloudConnection {

    private BlobStore blobStore;
    private BlobStoreContext blobStoreContext;
    private String provider;
    private String storageAccountName;
    private String storageAccountKey;
    private String containerName;
    private String baseFolder;

    private static JCloudConnection jCloudConnection;

    public JCloudConnection() {
        getPropValues();
    }

    public BlobStoreContext getBlobStoreContext() {
        if (this.blobStoreContext == null) {
            this.blobStoreContext = ContextBuilder.newBuilder(provider)
                    .credentials(storageAccountName, storageAccountKey)
                    .buildView(BlobStoreContext.class);
        }
        return this.blobStoreContext;
    }

    public static JCloudConnection getNewJcloudConnection() {
        // Only get one occurrence for now - may support more in the future
        if (jCloudConnection == null) {
            jCloudConnection = new JCloudConnection();
        }
        return jCloudConnection;
    }

    private void getPropValues() {
        // Todo This should be modified to use system properties....
        this.provider = System.getenv("JCLOUD_FILESYSTEMSTORE_PROVIDER");
        this.storageAccountName = System.getenv("JCLOUD_FILESYSTEMSTORE_STORAGEACCOUNTNAME");
        this.storageAccountKey = System.getenv("JCLOUD_FILESYSTEMSTORE_STORAGEACCOUNTKEY");
        this.containerName = System.getenv("JCLOUD_FILESYSTEMSTORE_CONTAINERNAME");
        this.baseFolder = System.getenv("JCLOUD_FILESYSTEMSTORE_BASEFOLDER");

        // Sample values
        //JCLOUD_FILESYSTEMSTORE_PROVIDER=azureblob
        //JCLOUD_FILESYSTEMSTORE_STORAGEACCOUNTNAME=<storage account name>
        //JCLOUD_FILESYSTEMSTORE_STORAGEACCOUNTKEY=<Key goes here>
        //JCLOUD_FILESYSTEMSTORE_CONTAINERNAME=<container name>
        //JCLOUD_FILESYSTEMSTORE_BASEFOLDER=geonetworkDataDir

    }

    public String getContainerName() {
        return containerName;
    }

    // Todo need to modify JcloudFilesystemStore so that is uses this base folder as a prefix to the container in case it is needed.
    //      Unless it is taken from the data dir folder?
    public String getBaseFolder() {
        return baseFolder;
    }
}
