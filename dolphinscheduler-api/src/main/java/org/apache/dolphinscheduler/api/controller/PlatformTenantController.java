/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.api.controller;

import static org.apache.dolphinscheduler.api.enums.Status.CREATE_TENANT_ERROR;
import static org.apache.dolphinscheduler.api.enums.Status.DELETE_TENANT_BY_ID_ERROR;
import static org.apache.dolphinscheduler.api.enums.Status.QUERY_TENANT_LIST_ERROR;
import static org.apache.dolphinscheduler.api.enums.Status.QUERY_TENANT_LIST_PAGING_ERROR;
import static org.apache.dolphinscheduler.api.enums.Status.UPDATE_TENANT_ERROR;
import static org.apache.dolphinscheduler.api.enums.Status.VERIFY_OS_TENANT_CODE_ERROR;

import org.apache.dolphinscheduler.api.dto.PrivateDomainDeployRequest;
import org.apache.dolphinscheduler.api.dto.PrivateDomainDeployResult;
import org.apache.dolphinscheduler.api.dto.PrivateDomainStatus;
import org.apache.dolphinscheduler.api.exceptions.ApiException;
import org.apache.dolphinscheduler.api.service.PlatformTenantService;
import org.apache.dolphinscheduler.api.utils.PageInfo;
import org.apache.dolphinscheduler.api.utils.Result;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.dao.entity.PlatformTenant;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.plugin.task.api.utils.ParameterUtils;

import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "PLATFORM_TENANT_TAG")
@RestController
@RequestMapping("/platform-tenants")
public class PlatformTenantController extends BaseController {

    @Autowired
    private PlatformTenantService platformTenantService;

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    @ApiException(CREATE_TENANT_ERROR)
    public Result<PlatformTenant> createTenant(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                               @RequestParam(value = "tenantCode") String tenantCode,
                                               @RequestParam(value = "tenantName") String tenantName,
                                               @RequestParam(value = "description", required = false) String description,
                                               @RequestParam(value = "adminUserIds") List<Integer> adminUserIds) {
        return Result.success(
                platformTenantService.createTenant(loginUser, tenantCode, tenantName, description, adminUserIds));
    }

    @GetMapping()
    @ResponseStatus(HttpStatus.OK)
    @ApiException(QUERY_TENANT_LIST_PAGING_ERROR)
    public Result<PageInfo<PlatformTenant>> queryTenantListPaging(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                                  @RequestParam(value = "searchVal", required = false) String searchVal,
                                                                  @RequestParam("pageNo") Integer pageNo,
                                                                  @RequestParam("pageSize") Integer pageSize) {
        checkPageParams(pageNo, pageSize);
        searchVal = ParameterUtils.handleEscapes(searchVal);
        return Result.success(platformTenantService.queryTenantList(loginUser, searchVal, pageNo, pageSize));
    }

    @GetMapping(value = "/list")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(QUERY_TENANT_LIST_ERROR)
    public Result<List<PlatformTenant>> queryTenantList(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser) {
        return Result.success(platformTenantService.queryTenantList(loginUser));
    }

    @PutMapping(value = "/{id}")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(UPDATE_TENANT_ERROR)
    public Result<PlatformTenant> updateTenant(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                               @PathVariable(value = "id") int id,
                                               @RequestParam(value = "tenantCode") String tenantCode,
                                               @RequestParam(value = "tenantName") String tenantName,
                                               @RequestParam(value = "description", required = false) String description,
                                               @RequestParam(value = "adminUserIds") List<Integer> adminUserIds) {
        return Result.success(
                platformTenantService.updateTenant(loginUser, id, tenantCode, tenantName, description, adminUserIds));
    }

    @DeleteMapping(value = "/{id}")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(DELETE_TENANT_BY_ID_ERROR)
    public Result<Boolean> deleteTenantById(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                            @PathVariable(value = "id") int id) {
        platformTenantService.deleteTenantById(loginUser, id);
        return Result.success(true);
    }

    @GetMapping(value = "/verify-code")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(VERIFY_OS_TENANT_CODE_ERROR)
    public Result<Boolean> verifyTenantCode(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                            @RequestParam(value = "tenantCode") String tenantCode) {
        platformTenantService.verifyTenantCode(tenantCode);
        return Result.success(true);
    }

    @PostMapping(value = "/switch")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(UPDATE_TENANT_ERROR)
    public Result<PlatformTenant> switchTenant(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                               @RequestParam(value = "platformTenantId") int platformTenantId,
                                               HttpServletRequest request) {
        return Result.success(platformTenantService.switchTenant(loginUser, getSessionId(request), platformTenantId));
    }

    @GetMapping(value = "/{id}/private-domain/status")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(QUERY_TENANT_LIST_ERROR)
    public Result<PrivateDomainStatus> queryPrivateDomainStatus(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                                @PathVariable(value = "id") int id,
                                                                HttpServletRequest request) {
        return Result.success(platformTenantService.queryPrivateDomainStatus(loginUser, id, request));
    }

    @PostMapping(value = "/{id}/private-domain/deploy")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(UPDATE_TENANT_ERROR)
    public Result<PrivateDomainDeployResult> deployPrivateDomain(@Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                                 @PathVariable(value = "id") int id,
                                                                 PrivateDomainDeployRequest deployRequest,
                                                                 HttpServletRequest request) {
        return Result.success(platformTenantService.deployPrivateDomain(loginUser, id, deployRequest, request));
    }

    private String getSessionId(HttpServletRequest request) {
        String sessionId = request.getHeader(Constants.SESSION_ID);
        if (StringUtils.isNotBlank(sessionId)) {
            return sessionId;
        }
        Cookie cookie = WebUtils.getCookie(request, Constants.SESSION_ID);
        return cookie == null ? null : cookie.getValue();
    }
}
