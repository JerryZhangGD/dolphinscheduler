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

package org.apache.dolphinscheduler.api.service;

import org.apache.dolphinscheduler.api.dto.PrivateDomainDeployRequest;
import org.apache.dolphinscheduler.api.dto.PrivateDomainDeployResult;
import org.apache.dolphinscheduler.api.dto.PrivateDomainStatus;
import org.apache.dolphinscheduler.api.utils.PageInfo;
import org.apache.dolphinscheduler.dao.entity.PlatformTenant;
import org.apache.dolphinscheduler.dao.entity.User;

import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

public interface PlatformTenantService {

    PlatformTenant createTenant(User loginUser,
                                String tenantCode,
                                String tenantName,
                                String description,
                                Collection<Integer> adminUserIds);

    PlatformTenant updateTenant(User loginUser,
                                int id,
                                String tenantCode,
                                String tenantName,
                                String description,
                                Collection<Integer> adminUserIds);

    void deleteTenantById(User loginUser, int id);

    PageInfo<PlatformTenant> queryTenantList(User loginUser, String searchVal, Integer pageNo, Integer pageSize);

    List<PlatformTenant> queryTenantList(User loginUser);

    List<PlatformTenant> queryTenantListByUserId(int userId);

    void verifyTenantCode(String tenantCode);

    PlatformTenant switchTenant(User loginUser, String sessionId, int platformTenantId);

    PrivateDomainStatus queryPrivateDomainStatus(User loginUser, int platformTenantId, HttpServletRequest request);

    PrivateDomainDeployResult deployPrivateDomain(User loginUser,
                                                  int platformTenantId,
                                                  PrivateDomainDeployRequest deployRequest,
                                                  HttpServletRequest request);

    PlatformTenant resolveCurrentTenant(User loginUser, String sessionId, Integer requestedPlatformTenantId);

    void grantUserTenants(User loginUser, int userId, Collection<Integer> platformTenantIds);

    void bindUserToDefaultTenant(int userId);

    void bindUserToCurrentTenant(User loginUser, int userId);
}
