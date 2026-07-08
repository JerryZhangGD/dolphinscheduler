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

package org.apache.dolphinscheduler.dao.mapper;

import org.apache.dolphinscheduler.common.thread.PlatformTenantContext;
import org.apache.dolphinscheduler.dao.entity.DataSource;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * datasource mapper interface
 */
public interface DataSourceMapper extends BaseMapper<DataSource> {

    /**
     * query datasource by type
     * @param userId userId
     * @param type type
     * @return datasource list
     */
    List<DataSource> queryDataSourceByType(@Param("userId") int userId,
                                           @Param("type") Integer type,
                                           @Param("platformTenantId") Integer platformTenantId);

    default List<DataSource> queryDataSourceByType(int userId, Integer type) {
        return queryDataSourceByType(userId, type, PlatformTenantContext.getPlatformTenantId());
    }

    /**
     * datasource page
     * @param page page
     * @param userId userId
     * @param name name
     * @return datasource IPage
     */
    IPage<DataSource> selectPaging(IPage<DataSource> page,
                                   @Param("userId") int userId,
                                   @Param("name") String name,
                                   @Param("platformTenantId") Integer platformTenantId);

    default IPage<DataSource> selectPaging(IPage<DataSource> page, int userId, String name) {
        return selectPaging(page, userId, name, PlatformTenantContext.getPlatformTenantId());
    }

    /**
     * query datasource by name
     * @param name name
     * @return datasource list
     */
    List<DataSource> queryDataSourceByName(@Param("name") String name,
                                           @Param("platformTenantId") Integer platformTenantId);

    default List<DataSource> queryDataSourceByName(String name) {
        return queryDataSourceByName(name, PlatformTenantContext.getPlatformTenantId());
    }

    /**
     * query authed datasource
     * @param userId userId
     * @return datasource list
     */
    List<DataSource> queryAuthedDatasource(@Param("userId") int userId,
                                           @Param("platformTenantId") Integer platformTenantId);

    default List<DataSource> queryAuthedDatasource(int userId) {
        return queryAuthedDatasource(userId, PlatformTenantContext.getPlatformTenantId());
    }

    /**
     * query datasource except userId
     * @param userId userId
     * @return datasource list
     */
    List<DataSource> queryDatasourceExceptUserId(@Param("userId") int userId,
                                                 @Param("platformTenantId") Integer platformTenantId);

    default List<DataSource> queryDatasourceExceptUserId(int userId) {
        return queryDatasourceExceptUserId(userId, PlatformTenantContext.getPlatformTenantId());
    }

    /**
     * list all datasource by type
     * @param type datasource type
     * @return datasource list
     */
    List<DataSource> listAllDataSourceByType(@Param("type") Integer type);

    /**
     * list authorized datasource
     *
     * @param userId userId
     * @param dataSourceIds data source id array
     * @param <T> T
     * @return datasource list
     */
    <T> List<DataSource> listAuthorizedDataSource(@Param("userId") int userId,
                                                  @Param("dataSourceIds") T[] dataSourceIds,
                                                  @Param("platformTenantId") Integer platformTenantId);

    default <T> List<DataSource> listAuthorizedDataSource(int userId, T[] dataSourceIds) {
        return listAuthorizedDataSource(userId, dataSourceIds, PlatformTenantContext.getPlatformTenantId());
    }

    /**
     * query datasource by name and user id
     *
     * @param userId userId
     * @param name   datasource name
     * @return If the name does not exist or the user does not have permission, it will return null
     */
    DataSource queryDataSourceByNameAndUserId(@Param("userId") int userId, @Param("name") String name);

    /**
     * selectPagingByIds
     * @param dataSourcePage
     * @param dataSourceIds
     * @return
     */
    IPage<DataSource> selectPagingByIds(Page<DataSource> dataSourcePage,
                                        @Param("dataSourceIds") List<Integer> dataSourceIds,
                                        @Param("name") String name,
                                        @Param("platformTenantId") Integer platformTenantId);

    default IPage<DataSource> selectPagingByIds(Page<DataSource> dataSourcePage,
                                                List<Integer> dataSourceIds,
                                                String name) {
        return selectPagingByIds(dataSourcePage, dataSourceIds, name, PlatformTenantContext.getPlatformTenantId());
    }
}
