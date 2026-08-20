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

import { useUserStore } from '@/store/user/user'
import type { PlatformTenant, UserInfoRes } from '@/service/modules/users/types'

const PUBLIC_DOMAIN_PREFIX = '/public'
const ORIGINAL_API_PREFIX = '/dolphinscheduler'

const userRelatedPathPrefixes = [
  '/login',
  '/oauth2-provider',
  '/oidc-providers',
  '/cookies',
  '/signOut',
  '/users',
  '/platform-tenants',
  '/access-tokens'
]

const isAbsoluteUrl = (url: string) => /^https?:\/\//i.test(url)

const normalizePath = (url?: string) => {
  if (!url) return ''
  if (isAbsoluteUrl(url)) return url
  return url.charAt(0) === '/' ? url : `/${url}`
}

const getPathOnly = (url: string) => normalizePath(url).split('?')[0]

const getCurrentPlatformTenant = (): PlatformTenant | undefined => {
  const userStore = useUserStore()
  const userInfo = userStore.getUserInfo as UserInfoRes
  const platformTenantId =
    userStore.getPlatformTenantId || userInfo.currentPlatformTenantId

  return userInfo.platformTenants?.find(
    (tenant) => tenant.id === platformTenantId
  )
}

const getPrivateDomainPrefix = () => {
  const currentTenant = getCurrentPlatformTenant()
  return currentTenant?.tenantCode ? `/${currentTenant.tenantCode}` : ''
}

export const isUserRelatedRequest = (url?: string) => {
  const path = getPathOnly(url || '')
  if (!path || isAbsoluteUrl(path)) return false

  return userRelatedPathPrefixes.some(
    (prefix) => path === prefix || path.startsWith(`${prefix}/`)
  )
}

export const getPrivateDomainToken = () =>
  getCurrentPlatformTenant()?.privateAdminToken || ''

export const isPrivateDomainRequest = (url?: string) => {
  if (!url || isAbsoluteUrl(url)) return false

  const userStore = useUserStore()
  return (
    userStore.getDomainMode === 'private' &&
    !isUserRelatedRequest(url) &&
    Boolean(getPrivateDomainPrefix()) &&
    Boolean(getPrivateDomainToken())
  )
}

export const resolveDomainPath = (url?: string) => {
  if (!url || isAbsoluteUrl(url)) return url

  const normalizedUrl = normalizePath(url)
  if (isUserRelatedRequest(url)) {
    if (
      normalizedUrl === ORIGINAL_API_PREFIX ||
      normalizedUrl.startsWith(`${ORIGINAL_API_PREFIX}/`)
    ) {
      return normalizedUrl
    }
    return `${ORIGINAL_API_PREFIX}${normalizedUrl}`
  }

  const userStore = useUserStore()
  const domainPrefix =
    userStore.getDomainMode === 'private' && getPrivateDomainToken()
      ? getPrivateDomainPrefix()
      : PUBLIC_DOMAIN_PREFIX

  if (!domainPrefix) return normalizedUrl

  if (
    normalizedUrl === domainPrefix ||
    normalizedUrl.startsWith(`${domainPrefix}/`) ||
    normalizedUrl.startsWith(`${PUBLIC_DOMAIN_PREFIX}/`)
  ) {
    return normalizedUrl
  }

  return `${domainPrefix}${normalizedUrl}`
}
