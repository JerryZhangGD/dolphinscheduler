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

package org.apache.dolphinscheduler.dao.repository.impl;

import org.apache.dolphinscheduler.dao.entity.PlatformTenantUser;
import org.apache.dolphinscheduler.dao.mapper.PlatformTenantUserMapper;
import org.apache.dolphinscheduler.dao.repository.BaseDao;
import org.apache.dolphinscheduler.dao.repository.PlatformTenantUserDao;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import lombok.NonNull;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@Repository
public class PlatformTenantUserDaoImpl extends BaseDao<PlatformTenantUser, PlatformTenantUserMapper>
        implements
            PlatformTenantUserDao {

    public PlatformTenantUserDaoImpl(@NonNull PlatformTenantUserMapper platformTenantUserMapper) {
        super(platformTenantUserMapper);
    }

    @Override
    public List<PlatformTenantUser> queryByUserId(int userId) {
        PlatformTenantUser query = new PlatformTenantUser();
        query.setUserId(userId);
        return mybatisMapper.selectList(new QueryWrapper<>(query));
    }

    @Override
    public List<PlatformTenantUser> queryByPlatformTenantId(int platformTenantId) {
        PlatformTenantUser query = new PlatformTenantUser();
        query.setPlatformTenantId(platformTenantId);
        return mybatisMapper.selectList(new QueryWrapper<>(query));
    }

    @Override
    public boolean relationExists(int userId, int platformTenantId) {
        PlatformTenantUser query = new PlatformTenantUser();
        query.setUserId(userId);
        query.setPlatformTenantId(platformTenantId);
        return mybatisMapper.selectCount(new QueryWrapper<>(query)) > 0;
    }

    @Override
    public void deleteByUserId(int userId) {
        PlatformTenantUser query = new PlatformTenantUser();
        query.setUserId(userId);
        mybatisMapper.delete(new QueryWrapper<>(query));
    }

    @Override
    public void deleteByPlatformTenantId(int platformTenantId) {
        PlatformTenantUser query = new PlatformTenantUser();
        query.setPlatformTenantId(platformTenantId);
        mybatisMapper.delete(new QueryWrapper<>(query));
    }

    @Override
    public void deleteByUserIdAndTenantIds(int userId, Collection<Integer> platformTenantIds) {
        if (platformTenantIds == null || platformTenantIds.isEmpty()) {
            return;
        }
        mybatisMapper.delete(new QueryWrapper<PlatformTenantUser>()
                .eq("user_id", userId)
                .in("platform_tenant_id", Collections.unmodifiableCollection(platformTenantIds)));
    }
}
