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

import com.google.common.net.UrlEscapers;
import jeeves.server.context.ServiceContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.fao.geonet.ApplicationContextHolder;
import org.fao.geonet.api.exception.ResourceAlreadyExistException;
import org.fao.geonet.api.exception.ResourceNotFoundException;
import org.fao.geonet.domain.MetadataResource;
import org.fao.geonet.domain.MetadataResourceVisibility;
import org.fao.geonet.kernel.AccessManager;
import org.fao.geonet.kernel.DataManager;
import org.fao.geonet.kernel.GeonetworkDataDirectory;
import org.fao.geonet.kernel.datamanager.IMetadataUtils;
import org.fao.geonet.kernel.setting.SettingManager;
import org.fao.geonet.lib.Lib;
import org.fao.geonet.repository.MetadataRepository;
import org.fao.geonet.utils.IO;
import org.springframework.context.ApplicationContext;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A FileSystemStore store resources files in the catalog data directory. Each metadata record as a
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
public abstract class AbstractFilesystemStore implements Store {

    public AbstractFilesystemStore() {
    }

       @Override
    public List<MetadataResource> getResources(ServiceContext context, String metadataUuid,
                                               Sort sort,
                                               String filter) throws Exception {
    	return getResources(context, metadataUuid, sort, filter, true);
    }

    @Override
    public List<MetadataResource> getResources(ServiceContext context, String metadataUuid,
                                               Sort sort,
                                               String filter, Boolean approved) throws Exception {
        List<MetadataResource> resourceList = new ArrayList<>();
        ApplicationContext _appContext = ApplicationContextHolder.get();
        String metadataId = getAndCheckMetadataId(metadataUuid, approved);
        AccessManager accessManager = _appContext.getBean(AccessManager.class);
        boolean canEdit = accessManager.canEdit(context, metadataId);

        resourceList.addAll(getResources(context, metadataUuid, MetadataResourceVisibility.PUBLIC, filter, approved));
        if (canEdit) {
            resourceList.addAll(getResources(context, metadataUuid, MetadataResourceVisibility.PRIVATE, filter, approved));
        }

        if (sort == Sort.name) {
            Collections.sort(resourceList, MetadataResourceVisibility.sortByFileName);
        }

        return resourceList;
    }

    @Override
    @Deprecated
    public List<MetadataResource> getResources(ServiceContext context, String metadataUuid,
                                               MetadataResourceVisibility visibility,
                                               String filter) throws Exception {	
    	return getResources(context, metadataUuid, visibility, filter, true);
    }


 /**
     * TODO: To be improve
     */
    protected String getAndCheckMetadataId(String metadataUuid, Boolean approved) throws Exception {
        ApplicationContext _appContext = ApplicationContextHolder.get();
        String metadataId = String.valueOf(_appContext.getBean(IMetadataUtils.class).findOneByUuid(metadataUuid).getId());
        if (metadataId == null) {
            throw new ResourceNotFoundException(String.format(
                "Metadata with UUID '%s' not found.", metadataUuid
            ));
        }
 
        if(approved) {
        	 metadataId = String.valueOf(_appContext.getBean(MetadataRepository.class)
        			 				.findOneByUuid(metadataUuid).getId());
        }
        return metadataId;
    }

    protected void canEdit(ServiceContext context, String metadataUuid) throws Exception {
        canEdit(context, metadataUuid, null);
    }

    protected void canEdit(ServiceContext context, String metadataUuid,
                         MetadataResourceVisibility visibility) throws Exception {
        ApplicationContext _appContext = ApplicationContextHolder.get();
        String metadataId = getAndCheckMetadataId(metadataUuid, false);
        AccessManager accessManager = _appContext.getBean(AccessManager.class);
        boolean canEdit = accessManager.canEdit(context, metadataId);
        if ((visibility == null && !canEdit) ||
            (visibility == MetadataResourceVisibility.PRIVATE && !canEdit)) {
            throw new SecurityException(String.format(
                "User does not have privileges to access '%s' resources for metadata '%s'.",
                visibility == null ? "any" : visibility,
                metadataUuid));
        }
    }
}
