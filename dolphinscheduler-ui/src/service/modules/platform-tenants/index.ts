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

import { axios } from '@/service/service'
import {
  ListReq,
  PlatformTenantCodeReq,
  PlatformTenantReq,
  IdReq,
  SwitchPlatformTenantReq
} from './types'

export function queryPlatformTenantListPaging(params: ListReq): any {
  return axios({
    url: '/platform-tenants',
    method: 'get',
    params
  })
}

export function createPlatformTenant(data: PlatformTenantReq): any {
  return axios({
    url: '/platform-tenants',
    method: 'post',
    data
  })
}

export function queryPlatformTenantList(): any {
  return axios({
    url: '/platform-tenants/list',
    method: 'get'
  })
}

export function verifyPlatformTenantCode(params: PlatformTenantCodeReq): any {
  return axios({
    url: '/platform-tenants/verify-code',
    method: 'get',
    params
  })
}

export function updatePlatformTenant(
  data: PlatformTenantReq,
  idReq: IdReq
): any {
  return axios({
    url: `/platform-tenants/${idReq.id}`,
    method: 'put',
    data
  })
}

export function deletePlatformTenantById(id: number): any {
  return axios({
    url: `/platform-tenants/${id}`,
    method: 'delete'
  })
}

export function switchPlatformTenant(data: SwitchPlatformTenantReq): any {
  return axios({
    url: '/platform-tenants/switch',
    method: 'post',
    data
  })
}
