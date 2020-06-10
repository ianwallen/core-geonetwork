/*
 * Copyright (C) 2001-2016 Food and Agriculture Organization of the
 * United Nations (FAO-UN), United Nations World Food Programme (WFP)
 * and United Nations Environment Programme (UNEP)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
 *
 * Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
 * Rome - Italy. email: geonetwork@osgeo.org
 */

package org.fao.geonet.api.records;

import static org.fao.geonet.api.ApiParams.API_CLASS_RECORD_OPS;
import static org.fao.geonet.api.ApiParams.API_CLASS_RECORD_TAG;
import static org.fao.geonet.api.ApiParams.API_PARAM_RECORD_UUID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.fao.geonet.ApplicationContextHolder;
import org.fao.geonet.api.API;
import org.fao.geonet.api.ApiParams;
import org.fao.geonet.api.ApiUtils;
import org.fao.geonet.api.exception.FeatureNotEnabledException;
import org.fao.geonet.api.exception.NotAllowedException;
import org.fao.geonet.api.exception.ResourceNotFoundException;
import org.fao.geonet.api.records.model.MetadataStatusParameter;
import org.fao.geonet.api.records.model.MetadataStatusResponse;
import org.fao.geonet.api.records.model.MetadataWorkflowStatusResponse;
import org.fao.geonet.api.tools.i18n.LanguageUtils;
import org.fao.geonet.domain.AbstractMetadata;
import org.fao.geonet.domain.ISODate;
import org.fao.geonet.domain.Metadata;
import org.fao.geonet.domain.MetadataDraft;
import org.fao.geonet.domain.MetadataStatus;
import org.fao.geonet.domain.MetadataStatusId;
import org.fao.geonet.domain.MetadataStatusId_;
import org.fao.geonet.domain.MetadataStatus_;
import org.fao.geonet.domain.Pair;
import org.fao.geonet.domain.Profile;
import org.fao.geonet.domain.StatusValue;
import org.fao.geonet.domain.StatusValueType;
import org.fao.geonet.domain.User;
import org.fao.geonet.domain.User_;
import org.fao.geonet.domain.utils.ObjectJSONUtils;
import org.fao.geonet.events.history.RecordRestoredEvent;
import org.fao.geonet.kernel.AccessManager;
import org.fao.geonet.kernel.DataManager;
import org.fao.geonet.kernel.datamanager.IMetadataIndexer;
import org.fao.geonet.kernel.datamanager.IMetadataManager;
import org.fao.geonet.kernel.datamanager.IMetadataStatus;
import org.fao.geonet.kernel.datamanager.IMetadataUtils;
import org.fao.geonet.kernel.metadata.StatusActions;
import org.fao.geonet.kernel.metadata.StatusActionsFactory;
import org.fao.geonet.kernel.search.LuceneSearcher;
import org.fao.geonet.kernel.setting.SettingManager;
import org.fao.geonet.kernel.setting.Settings;
import org.fao.geonet.repository.MetadataDraftRepository;
import org.fao.geonet.repository.MetadataStatusRepository;
import org.fao.geonet.repository.SortUtils;
import org.fao.geonet.repository.StatusValueRepository;
import org.fao.geonet.repository.Updater;
import org.fao.geonet.repository.UserRepository;
import org.fao.geonet.utils.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.sun.istack.NotNull;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jeeves.server.UserSession;
import jeeves.server.context.ServiceContext;
import jeeves.services.ReadWriteController;

@RequestMapping(value = { "/{portal}/api/records", "/{portal}/api/" + API.VERSION_0_1 + "/records" })
@Api(value = API_CLASS_RECORD_TAG, tags = API_CLASS_RECORD_TAG, description = API_CLASS_RECORD_OPS)
@Controller("recordWorkflow")
@ReadWriteController
public class MetadataWorkflowApi {

    @Autowired
    LanguageUtils languageUtils;

    @Autowired
    MetadataStatusRepository metadataStatusRepository;

    @Autowired
    MetadataDraftRepository metadatadraftRepository;

    @Autowired
    StatusValueRepository statusValueRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    IMetadataStatus metadataStatus;

    @Autowired
    AccessManager accessManager;

    @Autowired
    SettingManager settingManager;

    @Autowired
    DataManager dataManager;

    @Autowired
    IMetadataIndexer metadataIndexer;

    @Autowired
    StatusActionsFactory statusActionFactory;

    @Autowired
    IMetadataUtils metadataUtils;

    @ApiOperation(value = "Get record status history", notes = "", nickname = "getRecordStatusHistory")
    @RequestMapping(value = "/{metadataUuid}/status", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public List<MetadataStatusResponse> getRecordStatusHistory(
            @ApiParam(value = API_PARAM_RECORD_UUID, required = true) @PathVariable String metadataUuid,
            @RequestParam(required = false) boolean details,
            @ApiParam(value = "Sort direction", required = false) @RequestParam(defaultValue = "DESC") Sort.Direction sortOrder,
            HttpServletRequest request) throws Exception {
        ServiceContext context = ApiUtils.createServiceContext(request);
        AbstractMetadata metadata = ApiUtils.canViewRecord(metadataUuid, request);

        String sortField = SortUtils.createPath(MetadataStatus_.id, MetadataStatusId_.changeDate);

        List<MetadataStatus> listOfStatus = metadataStatusRepository.findAllById_MetadataId(metadata.getId(),
                new Sort(sortOrder, sortField));

        List<MetadataStatusResponse> response = buildMetadataStatusResponses(listOfStatus, details,
                context.getLanguage());

        // TODO: Add paging
        return response;
    }

    @ApiOperation(value = "Get record status history by type", notes = "", nickname = "getRecordStatusHistoryByType")
    @RequestMapping(value = "/{metadataUuid}/status/{type}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public List<MetadataStatusResponse> getRecordStatusHistoryByType(
            @ApiParam(value = API_PARAM_RECORD_UUID, required = true) @PathVariable String metadataUuid,
            @ApiParam(value = "Type", required = true) @PathVariable StatusValueType type,
            @RequestParam(required = false) boolean details,
            @ApiParam(value = "Sort direction", required = false) @RequestParam(defaultValue = "DESC") Sort.Direction sortOrder,
            HttpServletRequest request) throws Exception {
        ServiceContext context = ApiUtils.createServiceContext(request);
        AbstractMetadata metadata = ApiUtils.canViewRecord(metadataUuid, request);

        String sortField = SortUtils.createPath(MetadataStatus_.id, MetadataStatusId_.changeDate);

        List<MetadataStatus> listOfStatus = metadataStatusRepository.findAllByIdAndByType(metadata.getId(), type,
                new Sort(sortOrder, sortField));

        List<MetadataStatusResponse> response = buildMetadataStatusResponses(listOfStatus, details,
                context.getLanguage());

        // TODO: Add paging
        return response;
    }

    @ApiOperation(value = "Get last workflow status for a record", notes = "", nickname = "getStatus")
    @RequestMapping(value = "/{metadataUuid}/status/workflow/last", method = RequestMethod.GET, produces = {
            MediaType.APPLICATION_JSON_VALUE })
    @PreAuthorize("hasRole('Editor')")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Record status."),
            @ApiResponse(code = 403, message = ApiParams.API_RESPONSE_NOT_ALLOWED_CAN_EDIT) })
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public MetadataWorkflowStatusResponse getStatus(
            @ApiParam(value = API_PARAM_RECORD_UUID, required = true) @PathVariable String metadataUuid,
            HttpServletRequest request) throws Exception {
        AbstractMetadata metadata = ApiUtils.canEditRecord(metadataUuid, request);
        ApplicationContext appContext = ApplicationContextHolder.get();
        Locale locale = languageUtils.parseAcceptLanguage(request.getLocales());
        ServiceContext context = ApiUtils.createServiceContext(request, locale.getISO3Language());

        // --- only allow the owner of the record to set its status
        if (!accessManager.isOwner(context, String.valueOf(metadata.getId()))) {
            throw new SecurityException(String.format(
                    "Only the owner of the metadata can get the status. User is not the owner of the metadata"));
        }

        MetadataStatus recordStatus = metadataStatus.getStatus(metadata.getId());

        List<StatusValue> elStatus = statusValueRepository.findAllByType(StatusValueType.workflow);

        // --- get the list of content reviewers for this metadata record
        Set<Integer> ids = new HashSet<Integer>();
        ids.add(Integer.valueOf(metadata.getId()));
        List<Pair<Integer, User>> reviewers = userRepository.findAllByGroupOwnerNameAndProfile(ids, Profile.Reviewer,
                SortUtils.createSort(User_.name));
        List<User> listOfReviewers = new ArrayList<>();
        for (Pair<Integer, User> reviewer : reviewers) {
            listOfReviewers.add(reviewer.two());
        }
        return new MetadataWorkflowStatusResponse(recordStatus, listOfReviewers,
                accessManager.hasEditPermission(context, metadata.getId() + ""), elStatus);

    }

    @ApiOperation(value = "Set the record status", notes = "", nickname = "setStatus")
    @RequestMapping(value = "/{metadataUuid}/status", method = RequestMethod.PUT)
    @PreAuthorize("hasRole('Editor')")
    @ApiResponses(value = { @ApiResponse(code = 204, message = "Status updated."),
            @ApiResponse(code = 400, message = "Metadata workflow not enabled."),
            @ApiResponse(code = 403, message = ApiParams.API_RESPONSE_NOT_ALLOWED_CAN_EDIT) })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setStatus(@ApiParam(value = API_PARAM_RECORD_UUID, required = true) @PathVariable String metadataUuid,
            @ApiParam(value = "Metadata status", required = true) @RequestBody(required = true) MetadataStatusParameter status,
            HttpServletRequest request) throws Exception {
        AbstractMetadata metadata = ApiUtils.canEditRecord(metadataUuid, request);
        ServiceContext context = ApiUtils.createServiceContext(request,
                languageUtils.getIso3langCode(request.getLocales()));

        boolean isMdWorkflowEnable = settingManager.getValueAsBool(Settings.METADATA_WORKFLOW_ENABLE);

        if (!isMdWorkflowEnable) {
            throw new FeatureNotEnabledException(
                    "Metadata workflow is disabled, can not be set the status of metadata");
        }

        // --- only allow the owner of the record to set its status
        if (!accessManager.isOwner(context, String.valueOf(metadata.getId()))) {
            throw new SecurityException(String.format(
                    "Only the owner of the metadata can set the status of this record. User is not the owner of the metadata."));
        }

        boolean isAllowedSubmitApproveInvalidMd = settingManager
                .getValueAsBool(Settings.METADATA_WORKFLOW_ALLOW_SUBMIT_APPROVE_INVALID_MD);
        if (((status.getStatus() == Integer.parseInt(StatusValue.Status.SUBMITTED))
                || (status.getStatus() == Integer.parseInt(StatusValue.Status.APPROVED)))
                && !isAllowedSubmitApproveInvalidMd) {

            boolean isInvalid = MetadataUtils.retrieveMetadataValidationStatus(metadata, context);

            if (isInvalid) {
                throw new Exception("Metadata is invalid: can't be submitted or approved");
            }
        }

        // --- use StatusActionsFactory and StatusActions class to
        // --- change status and carry out behaviours for status changes
        StatusActions sa = statusActionFactory.createStatusActions(context);

        int author = context.getUserSession().getUserIdAsInt();
        MetadataStatus metadataStatus = convertParameter(metadata.getId(), status, author);
        List<MetadataStatus> listOfStatusChange = new ArrayList<>(1);
        listOfStatusChange.add(metadataStatus);
        sa.onStatusChange(listOfStatusChange);

        // --- reindex metadata
        metadataIndexer.indexMetadata(String.valueOf(metadata.getId()), true, null);
    }

    @ApiOperation(value = "Close a record task", notes = "", nickname = "closeTask")
    @RequestMapping(value = "/{metadataUuid}/status/{statusId:[0-9]+}.{userId:[0-9]+}.{changeDate}/close", method = RequestMethod.PUT)
    @PreAuthorize("hasRole('Editor')")
    @ApiResponses(value = { @ApiResponse(code = 204, message = "Task closed."),
            @ApiResponse(code = 404, message = "Status not found."),
            @ApiResponse(code = 403, message = ApiParams.API_RESPONSE_NOT_ALLOWED_CAN_EDIT) })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeTask(@ApiParam(value = API_PARAM_RECORD_UUID, required = true) @PathVariable String metadataUuid,
            @ApiParam(value = "Status identifier", required = true) @PathVariable int statusId,
            @ApiParam(value = "User identifier", required = true) @PathVariable int userId,
            @ApiParam(value = "Change date", required = true) @PathVariable String changeDate,
            @ApiParam(value = "Close date", required = true) @RequestParam String closeDate, HttpServletRequest request)
            throws Exception {
        AbstractMetadata metadata = ApiUtils.canEditRecord(metadataUuid, request);

        MetadataStatus metadataStatus = metadataStatusRepository
                .findOne(new MetadataStatusId().setMetadataId(metadata.getId()).setStatusId(statusId).setUserId(userId)
                        .setChangeDate(new ISODate(changeDate)));
        if (metadataStatus != null) {
            metadataStatusRepository.update(metadataStatus.getId(),
                    entity -> entity.setCloseDate(new ISODate(closeDate)));
        } else {
            throw new ResourceNotFoundException(
                    String.format("Can't find metadata status for record '%d', user '%s' at date '%s'", metadataUuid,
                            userId, changeDate));
        }
    }

    @ApiOperation(value = "Delete a record status", notes = "", nickname = "deleteStatus")
    @RequestMapping(value = "/{metadataUuid}/status/{statusId:[0-9]+}.{userId:[0-9]+}.{changeDate}", method = RequestMethod.DELETE)
    @PreAuthorize("hasRole('Administrator')")
    @ApiResponses(value = { @ApiResponse(code = 204, message = "Status removed."),
            @ApiResponse(code = 404, message = "Status not found."),
            @ApiResponse(code = 403, message = ApiParams.API_RESPONSE_NOT_ALLOWED_ONLY_ADMIN) })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecordStatus(
            @ApiParam(value = API_PARAM_RECORD_UUID, required = true) @PathVariable String metadataUuid,
            @ApiParam(value = "Status identifier", required = true) @PathVariable int statusId,
            @ApiParam(value = "User identifier", required = true) @PathVariable int userId,
            @ApiParam(value = "Change date", required = true) @PathVariable String changeDate,
            HttpServletRequest request) throws Exception {
        AbstractMetadata metadata = ApiUtils.canEditRecord(metadataUuid, request);

        MetadataStatus metadataStatus = metadataStatusRepository
                .findOne(new MetadataStatusId().setMetadataId(metadata.getId()).setStatusId(statusId).setUserId(userId)
                        .setChangeDate(new ISODate(changeDate)));
        if (metadataStatus != null) {
            metadataStatusRepository.delete(metadataStatus);
            // TODO: Reindex record ?
        } else {
            throw new ResourceNotFoundException(
                    String.format("Can't find metadata status for record '%d', user '%s' at date '%s'", metadataUuid,
                            userId, changeDate));
        }
    }

    @ApiOperation(value = "Delete all record status", notes = "", nickname = "deleteAllRecordStatus")
    @RequestMapping(value = "/{metadataUuid}/status", method = RequestMethod.DELETE)
    @PreAuthorize("hasRole('Administrator')")
    @ApiResponses(value = { @ApiResponse(code = 204, message = "Status removed."),
        @ApiResponse(code = 404, message = "Status not found."),
        @ApiResponse(code = 403, message = ApiParams.API_RESPONSE_NOT_ALLOWED_ONLY_ADMIN) })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllRecordStatus(
        @ApiParam(value = API_PARAM_RECORD_UUID, required = true) @PathVariable String metadataUuid,
        HttpServletRequest request) throws Exception {
        AbstractMetadata metadata = ApiUtils.canEditRecord(metadataUuid, request);
        metadataStatusRepository.deleteAllById_MetadataId(metadata.getId());
    }

    @ApiOperation(value = "Search status", notes = "", nickname = "searchStatusByType")
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET, path = "/status/search")
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public List<MetadataStatusResponse> getStatusByType(
            @ApiParam(value = "One or more types to retrieve (ie. worflow, event, task). Default is all.", required = false) @RequestParam(required = false) StatusValueType[] type,
            @ApiParam(value = "All event details including XML changes. Responses are bigger. Default is false", required = false) @RequestParam(required = false) boolean details,
            @ApiParam(value = "One or more event author. Default is all.", required = false) @RequestParam(required = false) Integer[] author,
            @ApiParam(value = "One or more event owners. Default is all.", required = false) @RequestParam(required = false) Integer[] owner,
            @ApiParam(value = "One or more record identifier. Default is all.", required = false) @RequestParam(required = false) Integer[] record,
            @ApiParam(value = "Start date", required = false) @RequestParam(required = false) String dateFrom,
            @ApiParam(value = "End date", required = false) @RequestParam(required = false) String dateTo,
            @ApiParam(value = "From page", required = false) @RequestParam(required = false, defaultValue = "0") Integer from,
            @ApiParam(value = "Number of records to return", required = false) @RequestParam(required = false, defaultValue = "100") Integer size,
            HttpServletRequest request) throws Exception {
        ServiceContext context = ApiUtils.createServiceContext(request);

        Sort sortByStatusChangeDate = SortUtils.createSort(Sort.Direction.DESC, MetadataStatus_.id,
                MetadataStatusId_.changeDate);
        final PageRequest pageRequest = new PageRequest(from, size, sortByStatusChangeDate);

        List<MetadataStatus> metadataStatuses;
        if ((type != null && type.length > 0) || (author != null && author.length > 0)
                || (owner != null && owner.length > 0) || (record != null && record.length > 0)) {
            metadataStatuses = metadataStatusRepository.searchStatus(
                    type != null && type.length > 0 ? Arrays.asList(type) : null,
                    author != null && author.length > 0 ? Arrays.asList(author) : null,
                    owner != null && owner.length > 0 ? Arrays.asList(owner) : null,
                    record != null && record.length > 0 ? Arrays.asList(record) : null, dateFrom, dateTo, pageRequest);
        } else {
            metadataStatuses = metadataStatusRepository.findAll(pageRequest).getContent();
        }

        return buildMetadataStatusResponses(metadataStatuses, details, context.getLanguage());
    }

    /**
     * Convert request parameter to a metadata status.
     */
    public MetadataStatus convertParameter(int id, MetadataStatusParameter parameter, int author) throws Exception {
        StatusValue statusValue = statusValueRepository.findOne(parameter.getStatus());

        MetadataStatus metadataStatus = new MetadataStatus();

        MetadataStatusId mdStatusId = new MetadataStatusId().setStatusId(parameter.getStatus()).setMetadataId(id)
                .setChangeDate(new ISODate()).setUserId(author);

        metadataStatus.setId(mdStatusId);
        metadataStatus.setStatusValue(statusValue);

        if (parameter.getChangeMessage() != null) {
            metadataStatus.setChangeMessage(parameter.getChangeMessage());
        }
        if (StringUtils.isNotEmpty(parameter.getDueDate())) {
            metadataStatus.setDueDate(new ISODate(parameter.getDueDate()));
        }
        if (StringUtils.isNotEmpty(parameter.getCloseDate())) {
            metadataStatus.setCloseDate(new ISODate(parameter.getCloseDate()));
        }
        if (parameter.getOwner() != null) {
            metadataStatus.setOwner(parameter.getOwner());
        }
        return metadataStatus;
    }

    // The restore function currently supports these states
    static final Integer[] supportedStatuses = {
            Integer.parseInt(StatusValue.Events.RECORDUPDATED),
            Integer.parseInt(StatusValue.Events.RECORDPROCESSINGCHANGE),
            Integer.parseInt(StatusValue.Events.RECORDRESTORED)};

    @ApiOperation(
            value = "Get saved content from the status record before changes",
            notes = "",
            nickname = "showStatusBefore")
    @RequestMapping(
            value = "/{metadataUuid}/status/{statusId:[0-9]+}.{userId:[0-9]+}.{changeDate}/before",
            method = RequestMethod.GET,
            produces = {
                    MediaType.APPLICATION_XML_VALUE
            })

    @PreAuthorize("hasRole('Editor')")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Previous version of the record."),
            @ApiResponse(code = 403, message = ApiParams.API_RESPONSE_NOT_ALLOWED_CAN_EDIT)
    })
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public String showStatusBefore(
            @ApiParam(value = API_PARAM_RECORD_UUID, required = true) @PathVariable String metadataUuid,
            @ApiParam(value = "Status identifier", required = true) @PathVariable int statusId,
            @ApiParam(value = "User identifier", required = true) @PathVariable int userId,
            @ApiParam(value = "Change date", required = true) @PathVariable String changeDate,
            HttpServletRequest request
    )
            throws Exception {

        AbstractMetadata metadata = null;

        try {
            metadata = ApiUtils.canViewRecord(metadataUuid, request);
        } catch (SecurityException e) {
            Log.debug(API.LOG_MODULE_NAME, e.getMessage(), e);
            throw new NotAllowedException(ApiParams.API_RESPONSE_NOT_ALLOWED_CAN_VIEW);
        }

        MetadataStatus metadataStatus = metadataStatusRepository
                .findOne(new MetadataStatusId().setMetadataId(metadata.getId()).setStatusId(statusId).setUserId(userId)
                        .setChangeDate(new ISODate(changeDate)));
        if (metadataStatus == null) {
            throw new ResourceNotFoundException(
                    String.format("Can't find metadata status for record '%d', user '%s' at date '%s'", metadataUuid,
                            userId, changeDate));
        }

        if(!StatusValueType.event.equals(metadataStatus.getStatusValue().getType()) || !ArrayUtils.contains(supportedStatuses, statusId)) {
            if(!StatusValueType.event.equals(metadataStatus.getStatusValue().getType()) || !ArrayUtils.contains(supportedStatuses, statusId)) {
                throw new NotAllowedException("Unsupported action on status type '" + metadataStatus.getStatusValue().getType() + "' for metadata '" + metadata.getUuid() + "'. Supports status type '" +
                        StatusValueType.event + "' with the status id '" + Arrays.toString(supportedStatuses) + "'.");
            }
        }

        return metadataStatus.getPreviousState();
    }

    @ApiOperation(
            value = "Get saved content from the status record after changes",
            notes = "",
            nickname = "showStatusAfter")
    @RequestMapping(value = "/{metadataUuid}/status/{statusId:[0-9]+}.{userId:[0-9]+}.{changeDate}/after",
            method = RequestMethod.GET,
            produces = {
                    MediaType.APPLICATION_XML_VALUE
            })

    @PreAuthorize("hasRole('Editor')")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Version of the record after changes."),
            @ApiResponse(code = 403, message = ApiParams.API_RESPONSE_NOT_ALLOWED_CAN_EDIT)
    })
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public String showStatusAfter(
            @ApiParam(value = API_PARAM_RECORD_UUID, required = true) @PathVariable String metadataUuid,
            @ApiParam(value = "Status identifier", required = true) @PathVariable int statusId,
            @ApiParam(value = "User identifier", required = true) @PathVariable int userId,
            @ApiParam(value = "Change date", required = true) @PathVariable String changeDate,
            HttpServletRequest request
    )
            throws Exception {
        AbstractMetadata metadata = null;

        try {
            metadata = ApiUtils.canViewRecord(metadataUuid, request);
        } catch (SecurityException e) {
            Log.debug(API.LOG_MODULE_NAME, e.getMessage(), e);
            throw new NotAllowedException(ApiParams.API_RESPONSE_NOT_ALLOWED_CAN_VIEW);
        }

        MetadataStatus metadataStatus = metadataStatusRepository
                .findOne(new MetadataStatusId().setMetadataId(metadata.getId()).setStatusId(statusId).setUserId(userId)
                        .setChangeDate(new ISODate(changeDate)));
        if (metadataStatus == null) {
            throw new ResourceNotFoundException(
                    String.format("Can't find metadata status for record '%d', user '%s' at date '%s'", metadataUuid,
                            userId, changeDate));
        }

        if(!StatusValueType.event.equals(metadataStatus.getStatusValue().getType()) || !ArrayUtils.contains(supportedStatuses, statusId)) {
            throw new NotAllowedException("Unsupported action on status type '" + metadataStatus.getStatusValue().getType() + "' for metadata '" + metadata.getUuid() + "'. Supports status type '" +
                    StatusValueType.event + "' with the status id '" + Arrays.toString(supportedStatuses) + "'.");
        }

        return metadataStatus.getCurrentState();
    }

    @ApiOperation(
            value = "Restore saved content from a status record",
            notes = "",
            nickname = "restoreAtStatusSave")
    @RequestMapping(
            value = "/{metadataUuid}/status/{statusId:[0-9]+}.{userId:[0-9]+}.{changeDate}/restore",
            method = RequestMethod.POST
            )
    @PreAuthorize("hasRole('Editor')")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Record restored."),
            @ApiResponse(code = 403, message = ApiParams.API_RESPONSE_NOT_ALLOWED_CAN_EDIT)
    })
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public void restoreAtStatusSave(
            @ApiParam(value = API_PARAM_RECORD_UUID, required = true) @PathVariable String metadataUuid,
            @ApiParam(value = "Status identifier", required = true) @PathVariable int statusId,
            @ApiParam(value = "User identifier", required = true) @PathVariable int userId,
            @ApiParam(value = "Change date", required = true) @PathVariable String changeDate,
            HttpServletRequest request
            )
                    throws Exception {

        AbstractMetadata metadata = null;

        try {
            metadata = ApiUtils.canEditRecord(metadataUuid, request);
        } catch (SecurityException e) {
            Log.debug(API.LOG_MODULE_NAME, e.getMessage(), e);
            throw new NotAllowedException(ApiParams.API_RESPONSE_NOT_ALLOWED_CAN_EDIT);
        }

        MetadataStatus metadataStatus = metadataStatusRepository
                .findOne(new MetadataStatusId().setMetadataId(metadata.getId()).setStatusId(statusId).setUserId(userId)
                        .setChangeDate(new ISODate(changeDate)));
        if (metadataStatus == null) {
            throw new ResourceNotFoundException(
                    String.format("Can't find metadata status for record '%d', user '%s' at date '%s'", metadataUuid,
                            userId, changeDate));
        }

        if(!StatusValueType.event.equals(metadataStatus.getStatusValue().getType()) || !ArrayUtils.contains(supportedStatuses, statusId)) {
            throw new NotAllowedException("The recover for this element is not supported. Supports status type '" + 
                 StatusValueType.event + "' with the status id '" + Arrays.toString(supportedStatuses) + 
                 "'. Received status type '" + metadataStatus.getStatusValue().getType() + "' with status id '" + statusId + "'");
        }

        Locale locale = languageUtils.parseAcceptLanguage(request.getLocales());
        ServiceContext context = ApiUtils.createServiceContext(request, locale.getISO3Language());

        String xmlBefore = metadata.getData();

        MetadataDraft draft = metadatadraftRepository.findOneByUuid(metadata.getUuid());
        // If there is a draft record, the restore is made over the Draft record and not the approved one.
        if (draft != null) {
            context.getBean(IMetadataManager.class).update(draft.getId(), new Updater<MetadataDraft>() {
                @Override
                public void apply(@Nonnull MetadataDraft metadata) {
                    metadata.setData(metadataStatus.getPreviousState());
                }
            });
        } else {
            context.getBean(IMetadataManager.class).update(metadata.getId(), new Updater<Metadata>() {
                @Override
                public void apply(@Nonnull Metadata metadata) {
                    metadata.setData(metadataStatus.getPreviousState());
                }
            });
        }

        ApplicationContext applicationContext = ApplicationContextHolder.get();

        UserSession session = ApiUtils.getUserSession(request.getSession());
        if (session != null) {
            // Create a new event
            String xmlAfter = metadataStatus.getPreviousState();
            if (!xmlAfter.equals(xmlBefore)) {
                new RecordRestoredEvent(metadata.getId(), session.getUserIdAsInt(), xmlBefore, xmlAfter).publish(applicationContext);
            } else {
                throw new NotAllowedException("Error recovering metadata id " + metadata.getUuid() + ". Cannot recover record which are identical. Possibly already recovered.");
            }
        }
    }

    /**
     * Build a list of status with additional information about users (author and
     * owner of the status change).
     */
    @NotNull
    private List<MetadataStatusResponse> buildMetadataStatusResponses(List<MetadataStatus> listOfStatus,
            boolean details, String language) {
        List<MetadataStatusResponse> response = new ArrayList<>();

        // Add all user info in response
        Map<Integer, User> listOfUsers = new HashMap<>();

        // Collect all user info
        for (MetadataStatus s : listOfStatus) {
            if (listOfUsers.get(s.getId().getUserId()) == null) {
                listOfUsers.put(s.getId().getUserId(), userRepository.findOne(s.getId().getUserId()));
            }
            if (s.getOwner() != null && listOfUsers.get(s.getOwner()) == null) {
                listOfUsers.put(s.getOwner(), userRepository.findOne(s.getOwner()));
            }
        }

        Map<Integer, String> titles = new HashMap<>();
        Map<Integer, String> uuids = new HashMap<>();

        // Add all user info and record title to response
        for (MetadataStatus s : listOfStatus) {
            MetadataStatusResponse status = new MetadataStatusResponse(s, details);

            User author = listOfUsers.get(status.getId().getUserId());
            if (author != null) {
                status.setAuthorName(author.getName() + " " + author.getSurname());
                status.setAuthorEmail(author.getEmail());
            }
            if (s.getOwner() != null) {
                User owner = listOfUsers.get(status.getOwner());
                if (owner != null) {
                    status.setOwnerName(owner.getName() + " " + owner.getSurname());
                    status.setOwnerEmail(owner.getEmail());
                }
            }

            if (s.getStatusValue().getType().equals(StatusValueType.event)) {
                status.setCurrentStatus(extractCurrentStatus(s));
                status.setPreviousStatus(extractPreviousStatus(s));
            }

            String title = titles.get(s.getId().getMetadataId());
            if (title == null) {
                try {
                    // Collect metadata titles. For now we use Lucene
                    title = LuceneSearcher.getMetadataFromIndexById(language, s.getId().getMetadataId() + "", "title");
                    titles.put(s.getId().getMetadataId(), title);
                } catch (Exception e1) {
                }
            }
            status.setTitle(title);

            String uuid = uuids.get(s.getId().getMetadataId());
            if (uuid == null) {
                try {
                    // Collect metadata uuid.
                    uuid=metadataUtils.getMetadataUuid(Integer.toString(s.getId().getMetadataId()));
                    uuids.put(s.getId().getMetadataId(), uuid);
                } catch (Exception e1) {
                }
            }
            status.setUuid(uuid);

            response.add(status);
        }

        return response;
    }

    private String extractCurrentStatus(MetadataStatus s) {
        switch (Integer.toString(s.getStatusValue().getId())) {
            case StatusValue.Events.ATTACHMENTADDED:
                return s.getCurrentState();
            case StatusValue.Events.RECORDOWNERCHANGE:
                return ObjectJSONUtils.extractFieldFromJSONString(s.getCurrentState(), "owner", "name");
            case StatusValue.Events.RECORDGROUPOWNERCHANGE:
                return ObjectJSONUtils.extractFieldFromJSONString(s.getCurrentState(), "owner", "name");
            case StatusValue.Events.RECORDPROCESSINGCHANGE:
                return ObjectJSONUtils.extractFieldFromJSONString(s.getCurrentState(), "process");
            case StatusValue.Events.RECORDCATEGORYCHANGE:
                List<String> categories = ObjectJSONUtils.extractListOfFieldFromJSONString(s.getCurrentState(), "category",
                        "name");
                StringBuffer categoriesAsString = new StringBuffer("[ ");
                for (String categoryName : categories) {
                    categoriesAsString.append(categoryName + " ");
                }
                categoriesAsString.append("]");
                return categoriesAsString.toString();
            case StatusValue.Events.RECORDVALIDATIONTRIGGERED:
                return s.getCurrentState().equals("1") ? "OK" : "KO";
            default:
                return "";
        }
    }

    private String extractPreviousStatus(MetadataStatus s) {
        switch (Integer.toString(s.getStatusValue().getId())) {
            case StatusValue.Events.ATTACHMENTDELETED:
                return s.getPreviousState();
            case StatusValue.Events.RECORDOWNERCHANGE:
                return ObjectJSONUtils.extractFieldFromJSONString(s.getPreviousState(), "owner", "name");
            case StatusValue.Events.RECORDGROUPOWNERCHANGE:
                return ObjectJSONUtils.extractFieldFromJSONString(s.getPreviousState(), "owner", "name");
            default:
                return "";
        }
    }

}
