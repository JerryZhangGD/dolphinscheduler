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

package org.apache.dolphinscheduler.api.service.impl;

import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.PlatformTenantService;
import org.apache.dolphinscheduler.api.service.SessionService;
import org.apache.dolphinscheduler.api.utils.PageInfo;
import org.apache.dolphinscheduler.api.utils.RegexUtils;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.common.enums.UserType;
import org.apache.dolphinscheduler.dao.entity.PlatformTenant;
import org.apache.dolphinscheduler.dao.entity.PlatformTenantUser;
import org.apache.dolphinscheduler.dao.entity.Session;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.dao.repository.PlatformTenantDao;
import org.apache.dolphinscheduler.dao.repository.PlatformTenantUserDao;
import org.apache.dolphinscheduler.dao.repository.DataSourceDao;
import org.apache.dolphinscheduler.dao.repository.ProjectDao;
import org.apache.dolphinscheduler.dao.repository.SessionDao;
import org.apache.dolphinscheduler.dao.repository.UserDao;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service
@Slf4j
public class PlatformTenantServiceImpl extends BaseServiceImpl implements PlatformTenantService {

    private static final int PLATFORM_TENANT_CODE_MAX_LENGTH = 64;

    @Autowired
    private PlatformTenantDao platformTenantDao;

    @Autowired
    private PlatformTenantUserDao platformTenantUserDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private ProjectDao projectDao;

    @Autowired
    private DataSourceDao dataSourceDao;

    @Autowired
    private SessionDao sessionDao;

    @Autowired
    private SessionService sessionService;

    @Override
    @Transactional
    public PlatformTenant createTenant(User loginUser, String tenantCode, String tenantName, String description) {
        checkAdmin(loginUser);
        checkTenantParams(tenantCode, tenantName, description);
        if (platformTenantDao.queryByCode(tenantCode) != null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, tenantCode);
        }

        Date now = new Date();
        PlatformTenant tenant = new PlatformTenant();
        tenant.setTenantCode(tenantCode);
        tenant.setTenantName(tenantName);
        tenant.setDescription(description);
        tenant.setCreateTime(now);
        tenant.setUpdateTime(now);
        platformTenantDao.insert(tenant);
        bindUserToTenants(loginUser.getId(), Collections.singleton(tenant.getId()));
        return tenant;
    }

    @Override
    @Transactional
    public PlatformTenant updateTenant(User loginUser,
                                       int id,
                                       String tenantCode,
                                       String tenantName,
                                       String description) {
        checkAdmin(loginUser);
        checkTenantParams(tenantCode, tenantName, description);

        PlatformTenant existsTenant = platformTenantDao.queryById(id);
        if (existsTenant == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, id);
        }

        PlatformTenant tenantWithCode = platformTenantDao.queryByCode(tenantCode);
        if (tenantWithCode != null && !Objects.equals(tenantWithCode.getId(), id)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, tenantCode);
        }

        existsTenant.setTenantCode(tenantCode);
        existsTenant.setTenantName(tenantName);
        existsTenant.setDescription(description);
        existsTenant.setUpdateTime(new Date());
        platformTenantDao.updateById(existsTenant);
        return existsTenant;
    }

    @Override
    @Transactional
    public void deleteTenantById(User loginUser, int id) {
        checkAdmin(loginUser);
        if (id == Constants.DEFAULT_PLATFORM_TENANT_ID) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, id);
        }
        PlatformTenant tenant = platformTenantDao.queryById(id);
        if (tenant == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, id);
        }
        if (CollectionUtils.isNotEmpty(platformTenantUserDao.queryByPlatformTenantId(id))) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, id);
        }
        if (CollectionUtils.isNotEmpty(projectDao.queryAllProject(0, id))) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, id);
        }
        if (CollectionUtils.isNotEmpty(dataSourceDao.listAuthorizedDataSource(0, null, id))) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, id);
        }
        platformTenantUserDao.deleteByPlatformTenantId(id);
        platformTenantDao.deleteById(id);
    }

    @Override
    public PageInfo<PlatformTenant> queryTenantList(User loginUser, String searchVal, Integer pageNo, Integer pageSize) {
        checkAdmin(loginUser);
        Page<PlatformTenant> page = new Page<>(pageNo, pageSize);
        IPage<PlatformTenant> tenantPage = platformTenantDao.queryTenantPaging(page, searchVal);
        return PageInfo.of(tenantPage);
    }

    @Override
    public List<PlatformTenant> queryTenantList(User loginUser) {
        if (loginUser.getUserType() == UserType.ADMIN_USER) {
            return platformTenantDao.queryAll();
        }
        return queryTenantListByUserId(loginUser.getId());
    }

    @Override
    public List<PlatformTenant> queryTenantListByUserId(int userId) {
        return platformTenantDao.queryByUserId(userId);
    }

    @Override
    public void verifyTenantCode(String tenantCode) {
        if (StringUtils.isBlank(tenantCode) || platformTenantDao.queryByCode(tenantCode) != null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, tenantCode);
        }
    }

    @Override
    public PlatformTenant switchTenant(User loginUser, String sessionId, int platformTenantId) {
        return resolveAndFillCurrentTenant(loginUser, sessionId, platformTenantId);
    }

    @Override
    public PlatformTenant resolveCurrentTenant(User loginUser, String sessionId, Integer requestedPlatformTenantId) {
        Session session = StringUtils.isBlank(sessionId) ? null : sessionService.getSession(sessionId);
        Integer platformTenantId = requestedPlatformTenantId;
        if (platformTenantId == null && session != null) {
            platformTenantId = session.getPlatformTenantId();
        }
        return resolveAndFillCurrentTenant(loginUser, sessionId, platformTenantId);
    }

    @Override
    @Transactional
    public void grantUserTenants(User loginUser, int userId, Collection<Integer> platformTenantIds) {
        checkAdmin(loginUser);
        User user = userDao.queryById(userId);
        if (user == null) {
            throw new ServiceException(Status.USER_NOT_EXIST, userId);
        }
        if (CollectionUtils.isEmpty(platformTenantIds)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, Constants.PLATFORM_TENANT_ID);
        }

        Set<Integer> distinctTenantIds = platformTenantIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        for (Integer platformTenantId : distinctTenantIds) {
            if (platformTenantDao.queryById(platformTenantId) == null) {
                throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, platformTenantId);
            }
        }

        platformTenantUserDao.deleteByUserId(userId);
        bindUserToTenants(userId, distinctTenantIds);
    }

    @Override
    @Transactional
    public void bindUserToDefaultTenant(int userId) {
        bindUserToTenants(userId, Collections.singleton(Constants.DEFAULT_PLATFORM_TENANT_ID));
    }

    @Override
    @Transactional
    public void bindUserToCurrentTenant(User loginUser, int userId) {
        Integer currentPlatformTenantId = loginUser.getCurrentPlatformTenantId();
        if (currentPlatformTenantId == null) {
            currentPlatformTenantId = Constants.DEFAULT_PLATFORM_TENANT_ID;
        }
        bindUserToTenants(userId, Collections.singleton(currentPlatformTenantId));
    }

    private PlatformTenant resolveAndFillCurrentTenant(User loginUser, String sessionId, Integer platformTenantId) {
        List<PlatformTenant> tenants = queryTenantListByUserId(loginUser.getId());
        if (CollectionUtils.isEmpty(tenants)) {
            bindUserToDefaultTenant(loginUser.getId());
            tenants = queryTenantListByUserId(loginUser.getId());
        }
        if (CollectionUtils.isEmpty(tenants)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, Constants.PLATFORM_TENANT_ID);
        }

        PlatformTenant currentTenant = null;
        if (platformTenantId != null) {
            currentTenant = tenants.stream()
                    .filter(tenant -> Objects.equals(tenant.getId(), platformTenantId))
                    .findFirst()
                    .orElse(null);
            if (currentTenant == null) {
                throw new ServiceException(Status.USER_NO_OPERATION_PERM);
            }
        } else {
            currentTenant = tenants.get(0);
        }

        updateSessionPlatformTenant(sessionId, currentTenant.getId());
        fillUserPlatformTenant(loginUser, currentTenant, tenants);
        return currentTenant;
    }

    private void updateSessionPlatformTenant(String sessionId, Integer platformTenantId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        Session session = sessionDao.queryById(sessionId);
        if (session == null || Objects.equals(session.getPlatformTenantId(), platformTenantId)) {
            return;
        }
        session.setPlatformTenantId(platformTenantId);
        sessionDao.updateById(session);
    }

    private void fillUserPlatformTenant(User user, PlatformTenant currentTenant, List<PlatformTenant> tenants) {
        user.setCurrentPlatformTenantId(currentTenant.getId());
        user.setCurrentPlatformTenantCode(currentTenant.getTenantCode());
        user.setCurrentPlatformTenantName(currentTenant.getTenantName());
        user.setPlatformTenants(tenants);
    }

    private void bindUserToTenants(int userId, Collection<Integer> platformTenantIds) {
        Date now = new Date();
        for (Integer platformTenantId : platformTenantIds) {
            if (platformTenantUserDao.relationExists(userId, platformTenantId)) {
                continue;
            }
            PlatformTenantUser relation = new PlatformTenantUser();
            relation.setUserId(userId);
            relation.setPlatformTenantId(platformTenantId);
            relation.setCreateTime(now);
            relation.setUpdateTime(now);
            platformTenantUserDao.insert(relation);
        }
    }

    private void checkTenantParams(String tenantCode, String tenantName, String description) {
        if (StringUtils.isBlank(tenantCode) || StringUtils.length(tenantCode) > PLATFORM_TENANT_CODE_MAX_LENGTH
                || !RegexUtils.isValidLinuxUserName(tenantCode)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, tenantCode);
        }
        if (StringUtils.isBlank(tenantName) || StringUtils.length(tenantName) > PLATFORM_TENANT_CODE_MAX_LENGTH) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, tenantName);
        }
        if (checkDescriptionLength(description)) {
            throw new ServiceException(Status.DESCRIPTION_TOO_LONG_ERROR);
        }
    }

    private void checkAdmin(User loginUser) {
        if (loginUser.getUserType() != UserType.ADMIN_USER) {
            throw new ServiceException(Status.USER_NO_OPERATION_PERM);
        }
    }
}
