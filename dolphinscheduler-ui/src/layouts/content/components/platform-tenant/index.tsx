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

import { computed, defineComponent, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NSelect } from 'naive-ui'
import { switchPlatformTenant } from '@/service/modules/platform-tenants'
import { getUserInfo } from '@/service/modules/users'
import { useUserStore } from '@/store/user/user'
import type { PlatformTenant, UserInfoRes } from '@/service/modules/users/types'
import styles from './index.module.scss'

const PlatformTenantSwitcher = defineComponent({
  name: 'PlatformTenantSwitcher',
  setup() {
    const userStore = useUserStore()
    const router = useRouter()
    const loading = ref(false)

    const tenants = computed(() => {
      const userInfo = userStore.getUserInfo as UserInfoRes
      return userInfo.platformTenants || []
    })

    const options = computed(() =>
      tenants.value.map((tenant: PlatformTenant) => ({
        label: tenant.tenantName || tenant.tenantCode,
        value: tenant.id
      }))
    )

    const value = computed(() => {
      const userInfo = userStore.getUserInfo as UserInfoRes
      return userStore.getPlatformTenantId || userInfo.currentPlatformTenantId
    })

    const handleChange = async (platformTenantId: number) => {
      await switchPlatformTenant({ platformTenantId })
      userStore.setPlatformTenantId(platformTenantId)
      const userInfo = await getUserInfo()
      userStore.setUserInfo(userInfo)
      userStore.setPlatformTenantId(userInfo.currentPlatformTenantId ?? null)
      router.go(0)
    }

    const refreshUserInfo = async () => {
      if (loading.value) return
      loading.value = true
      try {
        const userInfo = await getUserInfo()
        userStore.setUserInfo(userInfo)
        userStore.setPlatformTenantId(userInfo.currentPlatformTenantId ?? null)
      } finally {
        loading.value = false
      }
    }

    onMounted(refreshUserInfo)

    return { options, value, loading, handleChange, refreshUserInfo }
  },
  render() {
    if (this.options.length <= 0) return null
    return (
      <NSelect
        class={styles.switcher}
        size='small'
        value={this.value}
        options={this.options}
        filterable
        loading={this.loading}
        onFocus={this.refreshUserInfo}
        onUpdateValue={this.handleChange}
      />
    )
  }
})

export default PlatformTenantSwitcher
