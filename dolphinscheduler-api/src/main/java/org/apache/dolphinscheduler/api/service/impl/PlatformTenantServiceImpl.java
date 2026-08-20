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

import org.apache.dolphinscheduler.api.dto.PrivateDomainDeployRequest;
import org.apache.dolphinscheduler.api.dto.PrivateDomainDeployResult;
import org.apache.dolphinscheduler.api.dto.PrivateDomainStatus;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.PlatformTenantService;
import org.apache.dolphinscheduler.api.service.SessionService;
import org.apache.dolphinscheduler.api.utils.PageInfo;
import org.apache.dolphinscheduler.api.utils.RegexUtils;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.common.enums.UserType;
import org.apache.dolphinscheduler.common.utils.CodeGenerateUtils;
import org.apache.dolphinscheduler.common.utils.EncryptionUtils;
import org.apache.dolphinscheduler.dao.entity.PlatformTenant;
import org.apache.dolphinscheduler.dao.entity.PlatformTenantUser;
import org.apache.dolphinscheduler.dao.entity.Project;
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
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceLoader;
import org.apache.sshd.common.util.security.SecurityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
@Slf4j
public class PlatformTenantServiceImpl extends BaseServiceImpl implements PlatformTenantService {

    private static final int PLATFORM_TENANT_CODE_MAX_LENGTH = 64;

    private static final int PRIVATE_DOMAIN_HTTP_TIMEOUT_MS = 5000;

    private static final int PRIVATE_DOMAIN_SSH_CONNECT_TIMEOUT_MS = 10000;

    private static final int PRIVATE_DOMAIN_SSH_COMMAND_TIMEOUT_MS = 600000;

    private static final int PRIVATE_DOMAIN_COMMAND_OUTPUT_MAX_LENGTH = 4000;

    private static final String DEFAULT_PRIVATE_DOMAIN_PROCESS_CHECK_COMMAND =
            "ps -ef | grep -E \"ApiApplicationServer|MasterServer|WorkerServer|AlertServer|StandaloneServer\" | grep -v grep";

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
    public PlatformTenant createTenant(User loginUser,
                                       String tenantCode,
                                       String tenantName,
                                       String description,
                                       Collection<Integer> adminUserIds) {
        checkAdmin(loginUser);
        checkTenantParams(tenantCode, tenantName, description);
        Set<Integer> adminIds = checkAdminUserIds(adminUserIds);
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
        syncPlatformTenantAdmins(tenant.getId(), adminIds);
        ensureDefaultProject(tenant.getId(), loginUser);
        fillTenantAdminUserIds(tenant);
        return tenant;
    }

    @Override
    @Transactional
    public PlatformTenant updateTenant(User loginUser,
                                       int id,
                                       String tenantCode,
                                       String tenantName,
                                       String description,
                                       Collection<Integer> adminUserIds) {
        checkAdmin(loginUser);
        checkTenantParams(tenantCode, tenantName, description);
        Set<Integer> adminIds = checkAdminUserIds(adminUserIds);

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
        syncPlatformTenantAdmins(id, adminIds);
        fillTenantAdminUserIds(existsTenant);
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
        fillTenantAdminUserIds(tenantPage.getRecords());
        return PageInfo.of(tenantPage);
    }

    @Override
    public List<PlatformTenant> queryTenantList(User loginUser) {
        List<PlatformTenant> tenants;
        if (loginUser.getUserType() == UserType.ADMIN_USER) {
            tenants = platformTenantDao.queryAll();
        } else {
            tenants = queryTenantListByUserId(loginUser.getId());
        }
        fillTenantAdminUserIds(tenants);
        return tenants;
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
    public PrivateDomainStatus queryPrivateDomainStatus(User loginUser,
                                                        int platformTenantId,
                                                        HttpServletRequest request) {
        PlatformTenant tenant = queryAccessibleTenant(loginUser, platformTenantId);
        return probePrivateDomain(tenant, request);
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public PrivateDomainDeployResult deployPrivateDomain(User loginUser,
                                                         int platformTenantId,
                                                         PrivateDomainDeployRequest deployRequest,
                                                         HttpServletRequest request) {
        PlatformTenant tenant = queryAccessibleTenant(loginUser, platformTenantId);
        checkPrivateDomainManagePermission(loginUser, tenant.getId());
        checkPrivateDomainDeployRequest(deployRequest);

        String privateAdminToken = generatePrivateAdminToken(tenant.getTenantCode());
        fillPrivateDomainDeployment(tenant, deployRequest, privateAdminToken);

        String commandOutput = executePrivateDomainDeployCommands(tenant, deployRequest);
        tenant.setUpdateTime(new Date());
        platformTenantDao.updateById(tenant);

        PrivateDomainStatus status = probePrivateDomain(tenant, request);
        return PrivateDomainDeployResult.builder()
                .available(status.isAvailable())
                .message(status.getMessage())
                .commandOutput(commandOutput)
                .privateAdminToken(privateAdminToken)
                .status(status)
                .platformTenant(tenant)
                .build();
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

        Set<Integer> removedTenantIds = platformTenantUserDao.queryByUserId(userId)
                .stream()
                .map(PlatformTenantUser::getPlatformTenantId)
                .filter(platformTenantId -> !distinctTenantIds.contains(platformTenantId))
                .collect(Collectors.toSet());
        platformTenantUserDao.deleteByUserIdAndTenantIds(userId, removedTenantIds);
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

    private void ensureDefaultProject(int platformTenantId, User loginUser) {
        if (projectDao.queryByName(Constants.DEFAULT_PROJECT_NAME, platformTenantId) != null) {
            return;
        }

        Date now = new Date();
        Project project = Project.builder()
                .name(Constants.DEFAULT_PROJECT_NAME)
                .code(CodeGenerateUtils.genCode())
                .description("")
                .userId(loginUser.getId())
                .platformTenantId(platformTenantId)
                .userName(loginUser.getUserName())
                .createTime(now)
                .updateTime(now)
                .build();
        try {
            projectDao.insert(project);
        } catch (DuplicateKeyException ex) {
            log.info("Default project already exists, platformTenantId:{}.", platformTenantId);
        }
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
        user.setCurrentPlatformTenantAdmin(
                isPlatformTenantAdmin(user.getId(), currentTenant.getId()));
        user.setPlatformTenants(tenants);
    }

    private void bindUserToTenants(int userId, Collection<Integer> platformTenantIds) {
        bindUserToTenants(userId, platformTenantIds, 0);
    }

    private void bindUserToTenants(int userId, Collection<Integer> platformTenantIds, int adminFlag) {
        Date now = new Date();
        for (Integer platformTenantId : platformTenantIds) {
            PlatformTenantUser relation =
                    platformTenantUserDao.queryByUserIdAndPlatformTenantId(userId, platformTenantId);
            if (relation != null) {
                if (adminFlag == 1 && !Objects.equals(relation.getAdminFlag(), adminFlag)) {
                    relation.setAdminFlag(adminFlag);
                    relation.setUpdateTime(now);
                    platformTenantUserDao.updateById(relation);
                }
                continue;
            }
            relation = new PlatformTenantUser();
            relation.setUserId(userId);
            relation.setPlatformTenantId(platformTenantId);
            relation.setAdminFlag(adminFlag);
            relation.setCreateTime(now);
            relation.setUpdateTime(now);
            platformTenantUserDao.insert(relation);
        }
    }

    private Set<Integer> checkAdminUserIds(Collection<Integer> adminUserIds) {
        if (CollectionUtils.isEmpty(adminUserIds)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "adminUserIds");
        }
        Set<Integer> distinctAdminUserIds = adminUserIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(distinctAdminUserIds)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "adminUserIds");
        }
        for (Integer adminUserId : distinctAdminUserIds) {
            if (userDao.queryById(adminUserId) == null) {
                throw new ServiceException(Status.USER_NOT_EXIST, adminUserId);
            }
        }
        return distinctAdminUserIds;
    }

    private void syncPlatformTenantAdmins(int platformTenantId, Set<Integer> adminUserIds) {
        Date now = new Date();
        List<PlatformTenantUser> currentAdminRelations =
                platformTenantUserDao.queryAdminsByPlatformTenantId(platformTenantId);
        for (PlatformTenantUser currentAdminRelation : currentAdminRelations) {
            if (adminUserIds.contains(currentAdminRelation.getUserId())) {
                continue;
            }
            currentAdminRelation.setAdminFlag(0);
            currentAdminRelation.setUpdateTime(now);
            platformTenantUserDao.updateById(currentAdminRelation);
        }
        for (Integer adminUserId : adminUserIds) {
            bindUserToTenants(adminUserId, Collections.singleton(platformTenantId), 1);
        }
    }

    private boolean isPlatformTenantAdmin(Integer userId, Integer platformTenantId) {
        if (userId == null || platformTenantId == null) {
            return false;
        }
        PlatformTenantUser relation = platformTenantUserDao.queryByUserIdAndPlatformTenantId(userId, platformTenantId);
        return relation != null && Objects.equals(relation.getAdminFlag(), 1);
    }

    private void fillTenantAdminUserIds(List<PlatformTenant> tenants) {
        if (CollectionUtils.isEmpty(tenants)) {
            return;
        }
        for (PlatformTenant tenant : tenants) {
            fillTenantAdminUserIds(tenant);
        }
    }

    private void fillTenantAdminUserIds(PlatformTenant tenant) {
        if (tenant == null || tenant.getId() == null) {
            return;
        }
        tenant.setAdminUserIds(platformTenantUserDao.queryAdminsByPlatformTenantId(tenant.getId())
                .stream()
                .map(PlatformTenantUser::getUserId)
                .collect(Collectors.toList()));
    }

    private PlatformTenant queryAccessibleTenant(User loginUser, int platformTenantId) {
        PlatformTenant tenant = platformTenantDao.queryById(platformTenantId);
        if (tenant == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, platformTenantId);
        }
        if (loginUser.getUserType() != UserType.ADMIN_USER
                && platformTenantUserDao.queryByUserIdAndPlatformTenantId(loginUser.getId(), platformTenantId) == null) {
            throw new ServiceException(Status.USER_NO_OPERATION_PERM);
        }
        ensurePrivateDomainDefaults(tenant);
        return tenant;
    }

    private void checkPrivateDomainManagePermission(User loginUser, int platformTenantId) {
        if (loginUser.getUserType() == UserType.ADMIN_USER || isPlatformTenantAdmin(loginUser.getId(), platformTenantId)) {
            return;
        }
        throw new ServiceException(Status.USER_NO_OPERATION_PERM);
    }

    private void ensurePrivateDomainDefaults(PlatformTenant tenant) {
        boolean changed = false;
        if (StringUtils.isBlank(tenant.getPrivateNginxProxyPath())) {
            tenant.setPrivateNginxProxyPath(defaultPrivateNginxProxyPath(tenant.getTenantCode()));
            changed = true;
        }
        if (StringUtils.isBlank(tenant.getPrivateProcessCheckCommand())) {
            tenant.setPrivateProcessCheckCommand(DEFAULT_PRIVATE_DOMAIN_PROCESS_CHECK_COMMAND);
            changed = true;
        }
        if (!changed) {
            return;
        }
        tenant.setUpdateTime(new Date());
        platformTenantDao.updateById(tenant);
    }

    private PrivateDomainStatus probePrivateDomain(PlatformTenant tenant, HttpServletRequest request) {
        String proxyPath = normalizeProxyPath(StringUtils.defaultIfBlank(
                tenant.getPrivateNginxProxyPath(),
                defaultPrivateNginxProxyPath(tenant.getTenantCode())));
        String probeUrl = buildPrivateDomainProbeUrl(proxyPath, request);
        if (StringUtils.isBlank(tenant.getPrivateAdminToken())) {
            return PrivateDomainStatus.builder()
                    .available(false)
                    .tenantCode(tenant.getTenantCode())
                    .proxyPath(proxyPath)
                    .probeUrl(probeUrl)
                    .message("PRIVATE_DOMAIN_TOKEN_NOT_CREATED")
                    .platformTenant(tenant)
                    .build();
        }
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(PRIVATE_DOMAIN_HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .writeTimeout(PRIVATE_DOMAIN_HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(PRIVATE_DOMAIN_HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build();
            Request probeRequest = new Request.Builder()
                    .url(probeUrl)
                    .addHeader("token", tenant.getPrivateAdminToken())
                    .build();
            try (Response response = client.newCall(probeRequest).execute()) {
                boolean available = response.isSuccessful();
                return PrivateDomainStatus.builder()
                        .available(available)
                        .tenantCode(tenant.getTenantCode())
                        .proxyPath(proxyPath)
                        .probeUrl(probeUrl)
                        .message(available ? "PRIVATE_DOMAIN_AVAILABLE" : "PRIVATE_DOMAIN_UNAVAILABLE")
                        .platformTenant(tenant)
                        .build();
            }
        } catch (Exception ex) {
            log.info("Probe private domain failed, tenantCode:{}, probeUrl:{}", tenant.getTenantCode(), probeUrl, ex);
            return PrivateDomainStatus.builder()
                    .available(false)
                    .tenantCode(tenant.getTenantCode())
                    .proxyPath(proxyPath)
                    .probeUrl(probeUrl)
                    .message("PRIVATE_DOMAIN_UNAVAILABLE")
                    .platformTenant(tenant)
                    .build();
        }
    }

    private String buildPrivateDomainProbeUrl(String proxyPath, HttpServletRequest request) {
        String scheme = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        if (StringUtils.isBlank(scheme)) {
            scheme = request.getScheme();
        }

        String host = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        if (StringUtils.isBlank(host)) {
            host = request.getHeader("Host");
        }
        if (StringUtils.isBlank(host)) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port > 0 && port != 80 && port != 443) {
                host = host + ":" + port;
            }
        }
        return scheme + "://" + host + proxyPath + "/monitor/MASTER";
    }

    private String firstHeaderValue(String headerValue) {
        if (StringUtils.isBlank(headerValue)) {
            return null;
        }
        return StringUtils.substringBefore(headerValue, ",").trim();
    }

    private void checkPrivateDomainDeployRequest(PrivateDomainDeployRequest deployRequest) {
        if (deployRequest == null
                || StringUtils.isBlank(deployRequest.getSshHost())
                || StringUtils.isBlank(deployRequest.getSshUser())
                || StringUtils.isBlank(deployRequest.getDeployIp())
                || StringUtils.isBlank(deployRequest.getDbType())
                || StringUtils.isBlank(deployRequest.getDeployCommand())
                || StringUtils.isAllBlank(deployRequest.getSshPassword(), deployRequest.getSshPrivateKey())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "private domain deploy params");
        }
        if (StringUtils.isAllBlank(deployRequest.getDbHost(), deployRequest.getDbUrl())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "dbHost or dbUrl");
        }
    }

    private void fillPrivateDomainDeployment(PlatformTenant tenant,
                                             PrivateDomainDeployRequest deployRequest,
                                             String privateAdminToken) {
        tenant.setPrivateAdminToken(privateAdminToken);
        tenant.setPrivateDeployIp(deployRequest.getDeployIp());
        tenant.setPrivateDbType(deployRequest.getDbType());
        tenant.setPrivateDbHost(deployRequest.getDbHost());
        tenant.setPrivateDbPort(deployRequest.getDbPort());
        tenant.setPrivateDbName(deployRequest.getDbName());
        tenant.setPrivateDbUser(deployRequest.getDbUser());
        tenant.setPrivateDbUrl(deployRequest.getDbUrl());
        tenant.setPrivateDeployPath(deployRequest.getDeployPath());
        tenant.setPrivateProcessCheckCommand(StringUtils.defaultIfBlank(
                deployRequest.getProcessCheckCommand(),
                DEFAULT_PRIVATE_DOMAIN_PROCESS_CHECK_COMMAND));
        tenant.setPrivateNginxProxyPath(defaultPrivateNginxProxyPath(tenant.getTenantCode()));
    }

    private String executePrivateDomainDeployCommands(PlatformTenant tenant, PrivateDomainDeployRequest deployRequest) {
        StringBuilder output = new StringBuilder();
        try (SshClient sshClient = SshClient.setUpDefaultClient()) {
            sshClient.start();
            try (ClientSession session = createSshSession(sshClient, deployRequest)) {
                appendCommandOutput(output, "deploy",
                        runRemoteCommand(session, resolveDeployCommand(deployRequest.getDeployCommand(), tenant,
                                deployRequest)));
                if (StringUtils.isNotBlank(deployRequest.getNginxConfigCommand())) {
                    appendCommandOutput(output, "nginx-config",
                            runRemoteCommand(session,
                                    resolveDeployCommand(deployRequest.getNginxConfigCommand(), tenant, deployRequest)));
                }
                if (StringUtils.isNotBlank(deployRequest.getNginxReloadCommand())) {
                    appendCommandOutput(output, "nginx-reload",
                            runRemoteCommand(session,
                                    resolveDeployCommand(deployRequest.getNginxReloadCommand(), tenant,
                                            deployRequest)));
                }
                if (StringUtils.isNotBlank(tenant.getPrivateProcessCheckCommand())) {
                    appendCommandOutput(output, "process-check",
                            runRemoteCommand(session,
                                    resolveDeployCommand(tenant.getPrivateProcessCheckCommand(), tenant,
                                            deployRequest)));
                }
            }
        } catch (Exception ex) {
            throw new ServiceException("deploy private domain failed: " + ex.getMessage(), ex);
        }
        return truncateCommandOutput(output.toString());
    }

    private ClientSession createSshSession(SshClient sshClient, PrivateDomainDeployRequest deployRequest)
            throws Exception {
        int sshPort = deployRequest.getSshPort() == null ? 22 : deployRequest.getSshPort();
        ClientSession session = sshClient.connect(deployRequest.getSshUser(), deployRequest.getSshHost(), sshPort)
                .verify(PRIVATE_DOMAIN_SSH_CONNECT_TIMEOUT_MS)
                .getSession();
        if (StringUtils.isNotBlank(deployRequest.getSshPassword())) {
            session.addPasswordIdentity(deployRequest.getSshPassword());
        }
        if (StringUtils.isNotBlank(deployRequest.getSshPrivateKey())) {
            KeyPairResourceLoader loader = SecurityUtils.getKeyPairResourceParser();
            Collection<KeyPair> keyPairs = loader.loadKeyPairs(null, null, null, deployRequest.getSshPrivateKey());
            for (KeyPair keyPair : keyPairs) {
                session.addPublicKeyIdentity(keyPair);
            }
        }
        if (!session.auth().verify(PRIVATE_DOMAIN_SSH_CONNECT_TIMEOUT_MS).isSuccess()) {
            throw new ServiceException("SSH auth failed");
        }
        return session;
    }

    private String runRemoteCommand(ClientSession session, String command) throws IOException {
        try (
                ChannelExec channel = session.createExecChannel(command);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ByteArrayOutputStream err = new ByteArrayOutputStream()) {
            channel.setOut(out);
            channel.setErr(err);
            channel.open().verify(PRIVATE_DOMAIN_SSH_CONNECT_TIMEOUT_MS);
            Set<ClientChannelEvent> events = channel.waitFor(
                    EnumSet.of(ClientChannelEvent.CLOSED, ClientChannelEvent.TIMEOUT),
                    PRIVATE_DOMAIN_SSH_COMMAND_TIMEOUT_MS);
            if (events.contains(ClientChannelEvent.TIMEOUT)) {
                throw new ServiceException("remote command timeout");
            }
            Integer exitStatus = channel.getExitStatus();
            String stdout = new String(out.toByteArray(), StandardCharsets.UTF_8);
            String stderr = new String(err.toByteArray(), StandardCharsets.UTF_8);
            if (exitStatus == null || exitStatus != 0) {
                throw new ServiceException("remote command failed, exitStatus: " + exitStatus + ", error: "
                        + truncateCommandOutput(stderr));
            }
            return stdout + (StringUtils.isBlank(stderr) ? "" : System.lineSeparator() + stderr);
        }
    }

    private String resolveDeployCommand(String command,
                                        PlatformTenant tenant,
                                        PrivateDomainDeployRequest deployRequest) {
        Map<String, String> variables = new HashMap<>();
        variables.put("tenantCode", tenant.getTenantCode());
        variables.put("privateAdminToken", tenant.getPrivateAdminToken());
        variables.put("deployIp", deployRequest.getDeployIp());
        variables.put("dbType", deployRequest.getDbType());
        variables.put("dbHost", deployRequest.getDbHost());
        variables.put("dbPort", deployRequest.getDbPort());
        variables.put("dbName", deployRequest.getDbName());
        variables.put("dbUser", deployRequest.getDbUser());
        variables.put("dbPassword", deployRequest.getDbPassword());
        variables.put("dbUrl", deployRequest.getDbUrl());
        variables.put("deployPath", deployRequest.getDeployPath());
        variables.put("nginxProxyPath", tenant.getPrivateNginxProxyPath());

        String resolvedCommand = command;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            resolvedCommand = StringUtils.replace(resolvedCommand, "${" + entry.getKey() + "}",
                    StringUtils.defaultString(entry.getValue()));
        }
        return resolvedCommand;
    }

    private void appendCommandOutput(StringBuilder output, String step, String stepOutput) {
        if (output.length() > 0) {
            output.append(System.lineSeparator());
        }
        output.append("[").append(step).append("]").append(System.lineSeparator())
                .append(StringUtils.defaultString(stepOutput));
    }

    private String truncateCommandOutput(String output) {
        if (StringUtils.length(output) <= PRIVATE_DOMAIN_COMMAND_OUTPUT_MAX_LENGTH) {
            return output;
        }
        return StringUtils.substring(output, 0, PRIVATE_DOMAIN_COMMAND_OUTPUT_MAX_LENGTH);
    }

    private String defaultPrivateNginxProxyPath(String tenantCode) {
        return normalizeProxyPath(tenantCode);
    }

    private String normalizeProxyPath(String proxyPath) {
        if (StringUtils.isBlank(proxyPath)) {
            return "";
        }
        String normalizedProxyPath = proxyPath.startsWith("/") ? proxyPath : "/" + proxyPath;
        return StringUtils.removeEnd(normalizedProxyPath, "/");
    }

    private String generatePrivateAdminToken(String tenantCode) {
        return EncryptionUtils.getMd5(tenantCode + UUID.randomUUID().toString() + System.currentTimeMillis());
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
