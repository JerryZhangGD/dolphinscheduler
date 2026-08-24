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

import type { PlatformTenant } from '@/service/modules/users/types'

interface ListReq {
  pageNo: number
  pageSize: number
  searchVal?: string
}

interface PlatformTenantCodeReq {
  tenantCode: string
}

interface PlatformTenantReq extends PlatformTenantCodeReq {
  tenantName: string
  description?: string
  adminUserIds: number[]
}

interface IdReq {
  id: number
}

interface SwitchPlatformTenantReq {
  platformTenantId: number
}

interface PrivateDomainStatus {
  available: boolean
  tenantCode: string
  proxyPath: string
  probeUrl: string
  message: string
  platformTenant: PlatformTenant
}

interface PrivateDomainDeployReq {
  sshHost: string
  sshPort?: number
  sshUser: string
  sshPassword?: string
  sshPrivateKey?: string
  privateIp: string
  privatePort: string
  privateAdminToken: string
  deployIp?: string
  dbType?: string
  dbHost?: string
  dbPort?: string
  dbName?: string
  dbUser?: string
  dbPassword?: string
  dbUrl?: string
  deployPath?: string
  processCheckCommand?: string
  deployCommand?: string
  nginxConfigCommand?: string
  nginxConfigFile?: string
  nginxReloadCommand?: string
}

interface PrivateDomainDeployResult {
  available: boolean
  message: string
  commandOutput?: string
  privateAdminToken?: string
  status: PrivateDomainStatus
  platformTenant: PlatformTenant
}

export {
  ListReq,
  PlatformTenantCodeReq,
  PlatformTenantReq,
  IdReq,
  SwitchPlatformTenantReq,
  PrivateDomainStatus,
  PrivateDomainDeployReq,
  PrivateDomainDeployResult
}
