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
import org.fao.geonet.ApplicationContextHolder;
import org.fao.geonet.domain.MetadataResource;
import org.fao.geonet.domain.MetadataResourceVisibility;
import org.fao.geonet.kernel.GeonetworkDataDirectory;
import org.fao.geonet.resources.Resources;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Metadata resource stored in the file system.
 *
 * Created by francois on 31/12/15.
 */
public class FilesystemStoreResource implements MetadataResource {
    private final String id;
    private final String url;
    private final MetadataResourceVisibility metadataResourceVisibility;
    private double size = -1;
    private String contentType;
    private String dataDirectoryRelativePath;

    public FilesystemStoreResource(String id,
                                   String baseUrl,
                                   MetadataResourceVisibility metadataResourceVisibility,
                                   double size,
                                   String dataDirectoryRelativePath) {
        // content type will be retreived when we needed
        this(id, baseUrl, metadataResourceVisibility, size, null, dataDirectoryRelativePath);
    }
    public FilesystemStoreResource(String id,
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
        return Files.readAllBytes(getPath());
    }

    @Override
    public String getContentType() throws IOException {
        if (contentType == null) {
            this.contentType = Resources.getFileContentType(getPath());
        }
        return contentType;
    }

    @Override
    public String getDataDirectoryRelativePath() {
        return dataDirectoryRelativePath;
    }

    public Path getPath() {
        ApplicationContext _appContext = ApplicationContextHolder.get();

        GeonetworkDataDirectory dataDirectory = _appContext.getBean(GeonetworkDataDirectory.class);
        Path dataDir = dataDirectory.getMetadataDataDir();

        return dataDir.resolve(this.dataDirectoryRelativePath);
    }
}
