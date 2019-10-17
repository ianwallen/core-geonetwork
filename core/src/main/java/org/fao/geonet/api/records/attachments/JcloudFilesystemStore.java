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

import com.google.common.io.ByteSource;
import com.google.common.net.UrlEscapers;
import io.searchbox.strings.StringUtils;
import jeeves.server.context.ServiceContext;
import org.apache.commons.io.FilenameUtils;
import org.fao.geonet.ApplicationContextHolder;
import org.fao.geonet.api.exception.ResourceAlreadyExistException;
import org.fao.geonet.api.exception.ResourceNotFoundException;
import org.fao.geonet.domain.MetadataResource;
import org.fao.geonet.domain.MetadataResourceVisibility;
import org.fao.geonet.kernel.AccessManager;
import org.fao.geonet.kernel.GeonetworkDataDirectory;
import org.fao.geonet.kernel.setting.SettingManager;
import org.fao.geonet.lib.Lib;
import org.fao.geonet.resources.Resources;
import org.fao.geonet.utils.IO;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.*;
import org.jclouds.blobstore.options.CopyOptions;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.springframework.context.ApplicationContext;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static de.regnis.q.sequence.core.QSequenceAssert.assertNotNull;

/**
 * A JcloudFileSystemStore store resources files in the catalog data directory. Each metadata record as a
 * directory in the data directory containing a public and a private folder.
 *
 * <pre>
 *     datadir
 *      |-{{sequence_folder}}
 *      |    |-{{metadata_id}}
 *      |    |    |-private
 *      |    |    |-public
 *      |    |        |--doc.pdf
 * </pre>
 */
public class JcloudFilesystemStore extends AbstractFilesystemStore {

    public static final String DEFAULT_FILTER = ".*";
    private String CLOUD_FOLDER_SEPARATOR="/"; // not sure if this is consistent for all clouds?

    private BlobStore blobStore;
    private BlobStoreContext blobStoreContext;
    private String containerName;

    public JcloudFilesystemStore() {
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
    public List<MetadataResource> getResources(ServiceContext context, String metadataUuid,
                                               MetadataResourceVisibility visibility,
                                               String filter, Boolean approved) throws Exception {
        ApplicationContext _appContext = ApplicationContextHolder.get();
        String metadataId = getAndCheckMetadataId(metadataUuid, approved);
        GeonetworkDataDirectory dataDirectory =
                _appContext.getBean(GeonetworkDataDirectory.class);
        SettingManager settingManager = _appContext.getBean(SettingManager.class);
        AccessManager accessManager = _appContext.getBean(AccessManager.class);

        boolean canEdit = accessManager.canEdit(context, metadataId);
        if (visibility == MetadataResourceVisibility.PRIVATE && !canEdit) {
            throw new SecurityException(String.format(
                    "User does not have privileges to get the list of '%s' resources for metadata '%s'.",
                    visibility, metadataUuid));
        }

        Path metadataDir = Lib.resource.getMetadataDir(dataDirectory, metadataId);
        Path resourceTypeDir = metadataDir.resolve(visibility.toString());

        List<MetadataResource> resourceList = new ArrayList<>();

        if (filter == null || filter.isEmpty()) {
            filter = DEFAULT_FILTER;
        } else {
            // The sample filter are "*.{jpg,JPG,jpeg,JPEG,png,PNG,gif,GIF}",
            // in this case we want to change that into a regular expression so we need to replace the {} by (), the "," by "|" and the "*" to ".*" and escape the "."
            // Not the most elegant solution and it may not work for all filters.
            filter = filter.replace("{", "(").replace("}", ")").replace(",", "|").replace(".", "\\.").replace("*", ".*");
        }

        ListContainerOptions opts = new ListContainerOptions();
        String path = metadataDir.toUri().toString();
        if (StringUtils.isNotBlank(path)) {
            opts.delimiter(CLOUD_FOLDER_SEPARATOR);
            opts.prefix(toBucketEntry(resourceTypeDir) + CLOUD_FOLDER_SEPARATOR);
        }

        String marker = null;
        do {
            if (marker != null) {
                opts.afterMarker(marker);
            }

            PageSet<? extends StorageMetadata> page = blobStore.list(containerName, opts);

            for (StorageMetadata storageMetadata : page) {
                // Only add to the list if it is a blob and it matches the filter.
                if (storageMetadata.getType()== StorageType.BLOB && FilenameUtils.getName(storageMetadata.getName()).matches(filter)) {
                    BlobMetadata blobMetadata = blobStore.blobMetadata(containerName, storageMetadata.getName());
                    MetadataResource resource = new JCloudFilesystemStoreResource(
                            UrlEscapers.urlFragmentEscaper().escape(metadataUuid) +
                                    CLOUD_FOLDER_SEPARATOR + "attachments" + CLOUD_FOLDER_SEPARATOR +
                                    UrlEscapers.urlFragmentEscaper().escape(FilenameUtils.getName(storageMetadata.getName())),
                            settingManager.getNodeURL() + "api" + CLOUD_FOLDER_SEPARATOR + "records" + CLOUD_FOLDER_SEPARATOR,
                            visibility,
                            blobMetadata.getContentMetadata().getContentLength(),
                            blobMetadata.getContentMetadata().getContentType(),
                            storageMetadata.getName()
                    );
                    resourceList.add(resource);
                }
            }
            marker = page.getNextMarker();
        } while (marker != null);

        Collections.sort(resourceList, MetadataResourceVisibility.sortByFileName);

        return resourceList;
    }
    @Override
    @Deprecated
    public MetadataResource getResource(ServiceContext context, String metadataUuid, String resourceId) throws Exception {
    	return getResource(context, metadataUuid, resourceId, true);
    }

    @Override
    public MetadataResource getResource(ServiceContext context, String metadataUuid, String resourceId, Boolean approved) throws Exception {
        // Those characters should not be allowed by URL structure
        if (resourceId.contains("..") ||
            resourceId.startsWith("/") ||
            resourceId.startsWith("file:/")) {
            throw new SecurityException(String.format(
                "Invalid resource identifier '%s'.",
                resourceId));
        }
        ApplicationContext _appContext = ApplicationContextHolder.get();
        AccessManager accessManager = _appContext.getBean(AccessManager.class);
        SettingManager settingManager = _appContext.getBean(SettingManager.class);
        GeonetworkDataDirectory dataDirectory = _appContext.getBean(GeonetworkDataDirectory.class);
        String metadataId = getAndCheckMetadataId(metadataUuid, approved);
        Path metadataDir = Lib.resource.getMetadataDir(dataDirectory, metadataId);

        Path resourceFile = null;
        MetadataResource resource = null;

        boolean canDownload = accessManager.canDownload(context, metadataId);
        for (MetadataResourceVisibility r : MetadataResourceVisibility.values()) {

            ListContainerOptions opts = new ListContainerOptions();
                opts.delimiter(CLOUD_FOLDER_SEPARATOR);
                String path=toBucketEntry(metadataDir.resolve(r.toString())) + CLOUD_FOLDER_SEPARATOR + resourceId;
                opts.prefix(path);

                PageSet<? extends StorageMetadata> page = blobStore.list(containerName, opts);


            for (StorageMetadata storageMetadata : page) {
                if (storageMetadata.getType() == StorageType.BLOB) {
// Todo may need to copy file locally - as the path is not corect.
                    resourceFile = metadataDir.resolve(storageMetadata.getName());
                    BlobMetadata blobMetadata = blobStore.blobMetadata(containerName, storageMetadata.getName());
                    resource = new JCloudFilesystemStoreResource(
                            UrlEscapers.urlFragmentEscaper().escape(metadataUuid) +
                                    CLOUD_FOLDER_SEPARATOR + "attachments" + CLOUD_FOLDER_SEPARATOR +
                                    UrlEscapers.urlFragmentEscaper().escape(FilenameUtils.getName(storageMetadata.getName())),
                            settingManager.getNodeURL() + "api" + CLOUD_FOLDER_SEPARATOR + "records" + CLOUD_FOLDER_SEPARATOR,
                            MetadataResourceVisibility.parse(resourceFile.getParent().getFileName().toString()),
                            blobMetadata.getContentMetadata().getContentLength(),
                            blobMetadata.getContentMetadata().getContentType(),
                            storageMetadata.getName()
                    );
                }
            }
        }

        // Todo this will fail because the file does not exists.
        if (resource != null && blobStore.blobExists(containerName, resource.getDataDirectoryRelativePath())) {
            if (resourceFile.getParent().getFileName().toString().equals(
                MetadataResourceVisibility.PRIVATE.toString()) && !canDownload) {
                throw new SecurityException(String.format(
                    "Current user can't download resources for metadata '%s' and as such can't access the requested resource '%s'.",
                    metadataUuid, resourceId));
            }
            return resource;
        } else {
            throw new ResourceNotFoundException(String.format(
                "Metadata resource '%s' not found for metadata '%s'",
                resourceId, metadataUuid));
        }
    }


    private MetadataResource getResourceDescription(String metadataUuid, MetadataResourceVisibility visibility, String filePath) {
        ApplicationContext _appContext = ApplicationContextHolder.get();
        SettingManager settingManager = _appContext.getBean(SettingManager.class);


        double fileSize = Double.NaN;
        BlobMetadata blobMetadata = blobStore.blobMetadata(containerName, filePath);
        fileSize = blobMetadata.getSize();

        return new JCloudFilesystemStoreResource(
                metadataUuid + CLOUD_FOLDER_SEPARATOR + "attachments" + CLOUD_FOLDER_SEPARATOR + FilenameUtils.getName(blobMetadata.getName()),
                settingManager.getNodeURL() + "api" + CLOUD_FOLDER_SEPARATOR + "records" + CLOUD_FOLDER_SEPARATOR,
                visibility,
                blobMetadata.getContentMetadata().getContentLength(),
                blobMetadata.getContentMetadata().getContentType(),
                filePath
        );
    }


    @Override
    public MetadataResource putResource(ServiceContext context, String metadataUuid,
                                        MultipartFile file,
                                        MetadataResourceVisibility visibility) throws Exception {
    	return putResource(context, metadataUuid, file, visibility, true);
    }


    @Override
    public MetadataResource putResource(ServiceContext context, String metadataUuid,
                                        MultipartFile file,
                                        MetadataResourceVisibility visibility,
                                        Boolean approved) throws Exception {
        canEdit(context, metadataUuid);
        String filePath = getPath(metadataUuid, visibility, file.getOriginalFilename(), approved);

        // Create a blob.
        Blob blob = blobStoreContext.getBlobStore().blobBuilder(filePath)
                .payload(file.getBytes())  // or InputStream
                .contentLength(file.getBytes().length)
                .contentType(file.getContentType())
                .build();

        // Upload the Blob
        blobStore.putBlob(containerName, blob);

        return getResourceDescription(metadataUuid, visibility, filePath);
    }

    @Override
    public MetadataResource putResource(ServiceContext context, String metadataUuid, Path file, MetadataResourceVisibility visibility) throws Exception {
    	return putResource(context, metadataUuid, file, visibility, true);
    }

    @Override
    public MetadataResource putResource(ServiceContext context, String metadataUuid, Path file, MetadataResourceVisibility visibility, Boolean approved) throws Exception {
        canEdit(context, metadataUuid);
        String filePath = getPath(metadataUuid, visibility, file.getFileName().toString(), approved);

        // Create a blob.
        ByteSource payload = com.google.common.io.Files.asByteSource(file.toFile());
        Blob blob = blobStoreContext.getBlobStore().blobBuilder(filePath)
                .payload(payload)  // or InputStream
                .contentLength(payload.size())
                .contentType(Resources.getFileContentType(file))
                .build();

        // Upload the Blob
        blobStore.putBlob(containerName, blob);

        return getResourceDescription(metadataUuid, visibility, filePath);
    }

    @Override
    public MetadataResource putResource(ServiceContext context, String metadataUuid, URL fileUrl, MetadataResourceVisibility visibility) throws Exception {
    	return putResource(context, metadataUuid, fileUrl, visibility, true);
    }

    @Override
    public MetadataResource putResource(ServiceContext context, String metadataUuid, URL fileUrl, MetadataResourceVisibility visibility, Boolean approved) throws Exception {
        canEdit(context, metadataUuid);
        String fileName = FilenameUtils.getName(fileUrl.getPath());
        if (fileName.contains("?")) {
            fileName = fileName.substring(0, fileName.indexOf("?"));
        }

        String filePath = getPath(metadataUuid, visibility, fileName, approved);

        URLHeader urlHeader = getURLHeader(fileUrl);
        if (urlHeader ==  null) {
            urlHeader = new URLHeader();
        }
        // Create a blob.
        Blob blob = blobStoreContext.getBlobStore().blobBuilder(filePath)
                .payload(fileUrl.openStream())  // or InputStream
                .contentLength(urlHeader.contentLength)
                .contentType(urlHeader.contentType)
                .build();

        // Upload the Blob
        blobStore.putBlob(containerName, blob);

        return getResourceDescription(metadataUuid, visibility, filePath);
    }

    private static class URLHeader {
        String contentType;
        int contentLength;
    }
    private static URLHeader getURLHeader(URL url) {
        URLHeader urlHeader = new URLHeader();
        URLConnection conn = null;
        try {
            conn = url.openConnection();
            if(conn instanceof HttpURLConnection) {
                ((HttpURLConnection)conn).setRequestMethod("HEAD");
            }
            conn.getInputStream();
            urlHeader.contentLength=conn.getContentLength();
            urlHeader.contentType=conn.getContentType();
            return urlHeader;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if(conn instanceof HttpURLConnection) {
                ((HttpURLConnection)conn).disconnect();
            }
        }
    }

    private String getPath(String metadataUuid, MetadataResourceVisibility visibility, String fileName, Boolean approved) throws Exception {
        ApplicationContext _appContext = ApplicationContextHolder.get();
        GeonetworkDataDirectory dataDirectory = _appContext.getBean(GeonetworkDataDirectory.class);
        String metadataId = getAndCheckMetadataId(metadataUuid, approved);
        Path metadataDir = Lib.resource.getMetadataDir(dataDirectory, metadataId);

        Path folderPath = metadataDir.resolve(visibility.toString());
        String filePath=toBucketEntry(folderPath) + CLOUD_FOLDER_SEPARATOR + fileName;
        if(blobStore.blobExists(containerName, filePath)) {
            throw new ResourceAlreadyExistException(String.format(
                "A resource with name '%s' and status '%s' already exists for metadata '%s'.",
                fileName, visibility, metadataUuid));
        }
        return filePath;
    }


    @Override
    public String delResource(ServiceContext context, String metadataUuid) throws Exception {
    	return delResource(context, metadataUuid, true);
    }

    @Override
    public String delResource(ServiceContext context, String metadataUuid, Boolean approved) throws Exception {
        ApplicationContext _appContext = ApplicationContextHolder.get();
        String metadataId = getAndCheckMetadataId(metadataUuid, approved);

        canEdit(context, metadataUuid);

        GeonetworkDataDirectory dataDirectory = _appContext.getBean(GeonetworkDataDirectory.class);
        Path metadataDir = Lib.resource.getMetadataDir(dataDirectory, metadataId);
        try {
            IO.deleteFileOrDirectory(metadataDir, true);
            return String.format("Metadata '%s' directory removed.", metadataUuid);
        } catch (Exception e) {
            return String.format("Unable to remove metadata '%s' directory.", metadataUuid);
        }
    }
    @Override
    public String delResource(ServiceContext context, String metadataUuid, String resourceId) throws Exception {
    	return delResource(context, metadataUuid, resourceId, true);
    }


    @Override
    public String delResource(ServiceContext context, String metadataUuid, String resourceId, Boolean approved) throws Exception {
        canEdit(context, metadataUuid);

        MetadataResource metadataResource = getResource(context, metadataUuid, resourceId, approved);

        blobStore.removeBlob(containerName, metadataResource.getDataDirectoryRelativePath());
        if (!blobStore.blobExists(containerName, metadataResource.getDataDirectoryRelativePath())) {
            return String.format("MetadataResource '%s' removed.", resourceId);
        } else {
            return String.format("Unable to remove resource '%s'.", resourceId);
        }
    }

    @Override
    public MetadataResource patchResourceStatus(ServiceContext context, String metadataUuid,
                                                String resourceId,
                                                MetadataResourceVisibility visibility) throws Exception {
    	return patchResourceStatus(context, metadataUuid, resourceId, visibility, true);
    }

    @Override
    public MetadataResource patchResourceStatus(ServiceContext context, String metadataUuid,
                                                String resourceId,
                                                MetadataResourceVisibility visibility, 
                                                Boolean approved) throws Exception {
        ApplicationContext _appContext = ApplicationContextHolder.get();
        AccessManager accessManager = _appContext.getBean(AccessManager.class);
        String metadataId = getAndCheckMetadataId(metadataUuid, approved);

        if (accessManager.canEdit(context, metadataId)) {
            MetadataResource metadataResource  = getResource(context, metadataUuid, resourceId, approved);

            GeonetworkDataDirectory dataDirectory = _appContext.getBean(GeonetworkDataDirectory.class);
            Path metadataDir = Lib.resource.getMetadataDir(dataDirectory, metadataId);
            Path newFolderPath = metadataDir
                .resolve(visibility.toString());

            Path newFilePath = newFolderPath
                .resolve(metadataResource.getFileName());
            blobStore.copyBlob(containerName, metadataResource.getDataDirectoryRelativePath(), containerName, toBucketEntry(newFilePath), CopyOptions.NONE);
            blobStore.removeBlob(containerName, metadataResource.getDataDirectoryRelativePath());
            return getResourceDescription(metadataUuid, visibility, toBucketEntry(newFilePath));
        } else {
            throw new SecurityException(String.format(
                "Current user can't edit metadata '%s' and as such can't change the resource status for '%s'.",
                metadataUuid, resourceId));
        }
    }

    String toBucketEntry(Path path) {

        assertNotNull(path);

        ApplicationContext _appContext = ApplicationContextHolder.get();
        GeonetworkDataDirectory dataDirectory =
                _appContext.getBean(GeonetworkDataDirectory.class);
        Path dataDir = dataDirectory.getMetadataDataDir();

        Path tmpPath = null;
        if (path.isAbsolute()) {
            // path should be relative from the dataDir
            tmpPath = dataDir.relativize(path);
        } else {
            tmpPath = path;
        }

        return tmpPath.toString().replace("\\", CLOUD_FOLDER_SEPARATOR);
    }

}
