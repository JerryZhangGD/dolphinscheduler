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

import cookies from 'js-cookie'
import { useUserStore } from '@/store/user/user'
import {
  getPrivateDomainToken,
  isPrivateDomainRequest,
  resolveDomainPath
} from '@/service/domain'

const apiPrefix =
  import.meta.env.MODE === 'development'
    ? ''
    : import.meta.env.VITE_APP_PROD_WEB_URL
const reSlashPrefix = /^\/+/

const resolveURL = (url: string) => {
  if (url.indexOf('http') === 0) {
    return url
  }

  const domainPath = resolveDomainPath(url) || url
  return `${apiPrefix}/${domainPath.replace(reSlashPrefix, '')}`
}

const appendQuery = (url: string, obj?: any) => {
  const params = new URLSearchParams()

  Object.keys(obj || {}).forEach((key) => {
    const value = obj[key]
    if (value === undefined || value === null) return

    if (Array.isArray(value)) {
      value.forEach((item) => params.append(key, String(item)))
      return
    }
    params.append(key, String(value))
  })

  const query = params.toString()
  if (!query) return url

  return `${url}${url.includes('?') ? '&' : '?'}${query}`
}

const resolveFileName = (response: Response, url: string) => {
  const disposition = response.headers.get('content-disposition') || ''
  const matchedFileName =
    disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1] ||
    disposition.match(/filename="?([^"]+)"?/i)?.[1]

  if (matchedFileName) {
    return decodeURIComponent(matchedFileName)
  }

  return url.split('?')[0].split('/').pop() || 'download'
}

const resolveHeaders = (url: string) => {
  const userStore = useUserStore()
  const headers: Record<string, string> = {}
  const language = cookies.get('language')
  if (language) headers.language = language

  if (isPrivateDomainRequest(url)) {
    headers.token = getPrivateDomainToken()
    return headers
  }

  headers.sessionId = userStore.getSessionId
  if (userStore.getPlatformTenantId) {
    headers.platformTenantId = String(userStore.getPlatformTenantId)
  }
  return headers
}

const downloadFile = async (url: string, obj?: any) => {
  const requestUrl = appendQuery(resolveURL(url), obj)
  const response = await fetch(requestUrl, {
    method: 'get',
    headers: resolveHeaders(url)
  })

  if (!response.ok) {
    window.$message?.error(response.statusText)
    return
  }

  const blob = await response.blob()
  const link = document.createElement('a')
  link.href = window.URL.createObjectURL(blob)
  link.download = resolveFileName(response, requestUrl)
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(link.href)
}

export default downloadFile
