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

import { computed, defineComponent, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NRadioButton,
  NRadioGroup,
  NSpace
} from 'naive-ui'
import {
  deployPrivateDomain,
  queryPrivateDomainStatus
} from '@/service/modules/platform-tenants'
import type {
  PrivateDomainDeployReq,
  PrivateDomainDeployResult,
  PrivateDomainStatus
} from '@/service/modules/platform-tenants/types'
import { useUserStore } from '@/store/user/user'
import type { DomainMode } from '@/store/user/types'
import type { PlatformTenant, UserInfoRes } from '@/service/modules/users/types'
import styles from './index.module.scss'

const DEFAULT_NGINX_RELOAD_COMMAND =
  '/bin/bash /opt/dolphinscheduler/bin/nginx-control.sh reload'

const DomainSwitcher = defineComponent({
  name: 'DomainSwitcher',
  setup() {
    const userStore = useUserStore()
    const router = useRouter()
    const checking = ref(false)
    const deploying = ref(false)
    const deployModalVisible = ref(false)
    const privateStatus = ref<PrivateDomainStatus | null>(null)
    const deployForm = reactive<PrivateDomainDeployReq>({
      sshHost: '',
      sshPort: 22,
      sshUser: '',
      sshPassword: '',
      sshPrivateKey: '',
      privateIp: '',
      privatePort: '12345',
      privateAdminToken: '',
      nginxConfigCommand: '',
      nginxConfigFile: '',
      nginxReloadCommand: DEFAULT_NGINX_RELOAD_COMMAND
    })

    const currentPlatformTenant = computed<PlatformTenant | undefined>(() => {
      const userInfo = userStore.getUserInfo as UserInfoRes
      const platformTenantId =
        userStore.getPlatformTenantId || userInfo.currentPlatformTenantId

      return userInfo.platformTenants?.find(
        (tenant) => tenant.id === platformTenantId
      )
    })

    const hasPrivateDomain = computed(() =>
      Boolean(currentPlatformTenant.value?.tenantCode)
    )

    const canDeployPrivateDomain = computed(() => {
      const userInfo = userStore.getUserInfo as UserInfoRes
      return (
        userInfo.userType === 'ADMIN_USER' ||
        Boolean(userInfo.currentPlatformTenantAdmin)
      )
    })

    const options = computed(() => [
      { label: '公域', value: 'public' },
      {
        label: '私域',
        value: 'private',
        disabled: !hasPrivateDomain.value || checking.value
      }
    ])

    const navigateHome = async () => {
      if (router.currentRoute.value.name === 'home') {
        router.go(0)
        return
      }
      await router.replace({ name: 'home' })
    }

    const enterDomain = async (domainMode: DomainMode) => {
      userStore.setDomainMode(domainMode)
      await navigateHome()
    }

    const updateStoredTenant = (tenant?: PlatformTenant) => {
      if (!tenant) return

      const userInfo = userStore.getUserInfo as UserInfoRes
      const platformTenants = userInfo.platformTenants || []
      userStore.setUserInfo({
        ...userInfo,
        currentPlatformTenantId: tenant.id,
        currentPlatformTenantCode: tenant.tenantCode,
        currentPlatformTenantName: tenant.tenantName,
        platformTenants: platformTenants.map((item) =>
          item.id === tenant.id ? { ...item, ...tenant } : item
        )
      })
      userStore.setPlatformTenantId(tenant.id)
    }

    const resetDeployForm = () => {
      const tenant = currentPlatformTenant.value
      const tenantConfigFile = tenant?.tenantCode
        ? `/opt/dolphinscheduler/conf/nginx/tenants/${tenant.tenantCode}.conf`
        : ''
      Object.assign(deployForm, {
        sshHost: tenant?.privateDeployIp || '',
        sshPort: 22,
        sshUser: '',
        sshPassword: '',
        sshPrivateKey: '',
        privateIp: tenant?.privateDeployIp || '',
        privatePort: tenant?.privateBackendPort || '12345',
        privateAdminToken: tenant?.privateAdminToken || '',
        nginxConfigCommand: '',
        nginxConfigFile: tenantConfigFile,
        nginxReloadCommand: DEFAULT_NGINX_RELOAD_COMMAND
      })
    }

    const openDeployModal = (status: PrivateDomainStatus) => {
      privateStatus.value = status
      resetDeployForm()
      deployModalVisible.value = true
    }

    const switchToPrivateDomain = async () => {
      const tenant = currentPlatformTenant.value
      if (!tenant?.id) return

      checking.value = true
      try {
        const status = (await queryPrivateDomainStatus(
          tenant.id
        )) as PrivateDomainStatus
        updateStoredTenant(status.platformTenant)
        if (status.available) {
          await enterDomain('private')
          return
        }
        openDeployModal(status)
      } finally {
        checking.value = false
      }
    }

    const handleChange = async (value: string | number) => {
      const domainMode = value as DomainMode
      if (domainMode === userStore.getDomainMode) return

      if (domainMode === 'private') {
        await switchToPrivateDomain()
        return
      }

      await enterDomain('public')
    }

    const handleDeploy = async () => {
      const tenant = currentPlatformTenant.value
      if (!tenant?.id) return

      deploying.value = true
      try {
        const result = (await deployPrivateDomain(tenant.id, {
          ...deployForm
        })) as PrivateDomainDeployResult
        const platformTenant =
          result.platformTenant || result.status?.platformTenant
        updateStoredTenant(
          platformTenant
            ? {
                ...platformTenant,
                privateAdminToken:
                  result.privateAdminToken || platformTenant.privateAdminToken
              }
            : undefined
        )
        privateStatus.value = result.status

        if (result.available) {
          window.$message.success('私域配置已生效')
          deployModalVisible.value = false
          await enterDomain('private')
          return
        }
        window.$message.warning('私域配置已保存，暂时未检测到可用服务')
      } finally {
        deploying.value = false
      }
    }

    const handleModalVisibleChange = (show: boolean) => {
      deployModalVisible.value = show
    }

    return {
      options,
      domainMode: computed(() => userStore.getDomainMode),
      checking,
      deploying,
      deployForm,
      deployModalVisible,
      privateStatus,
      canDeployPrivateDomain,
      handleChange,
      handleDeploy,
      handleModalVisibleChange
    }
  },
  render() {
    const renderTextInput = (
      label: string,
      value: string | undefined,
      update: (value: string) => void,
      type: 'text' | 'password' | 'textarea' = 'text',
      placeholder?: string
    ) => (
      <NFormItem label={label}>
        <NInput
          type={type}
          value={value}
          placeholder={placeholder}
          autosize={type === 'textarea' ? { minRows: 2, maxRows: 5 } : false}
          onUpdateValue={update}
        />
      </NFormItem>
    )

    return (
      <>
        <NRadioGroup
          class={styles.switcher}
          size='small'
          value={this.domainMode}
          onUpdateValue={this.handleChange}
        >
          {this.options.map((option) => (
            <NRadioButton value={option.value} disabled={option.disabled}>
              {option.label}
            </NRadioButton>
          ))}
        </NRadioGroup>
        <NModal
          show={this.deployModalVisible}
          preset='card'
          title='私域后端不可用'
          class={styles.deployModal}
          onUpdateShow={this.handleModalVisibleChange}
        >
          <NSpace vertical size='large'>
            <NAlert type='warning' showIcon={false}>
              当前平台租户的私域后端未响应，请填写私域接入信息后再切换。
            </NAlert>
            {this.canDeployPrivateDomain ? (
              <NForm labelPlacement='top'>
                <div class={styles.formGrid}>
                  {renderTextInput(
                    'Nginx SSH 地址',
                    this.deployForm.sshHost,
                    (v) => {
                      this.deployForm.sshHost = v
                    }
                  )}
                  <NFormItem label='Nginx SSH 端口'>
                    <NInputNumber
                      value={this.deployForm.sshPort}
                      min={1}
                      max={65535}
                      onUpdateValue={(v) => {
                        this.deployForm.sshPort = Number(v || 22)
                      }}
                    />
                  </NFormItem>
                  {renderTextInput(
                    'Nginx SSH 用户',
                    this.deployForm.sshUser,
                    (v) => {
                      this.deployForm.sshUser = v
                    }
                  )}
                  {renderTextInput(
                    'Nginx SSH 密码',
                    this.deployForm.sshPassword,
                    (v) => {
                      this.deployForm.sshPassword = v
                    },
                    'password'
                  )}
                  {renderTextInput(
                    '私域后端 IP',
                    this.deployForm.privateIp,
                    (v) => {
                      this.deployForm.privateIp = v
                    }
                  )}
                  <NFormItem label='私域后端端口'>
                    <NInputNumber
                      value={Number(this.deployForm.privatePort || 12345)}
                      min={1}
                      max={65535}
                      onUpdateValue={(v) => {
                        this.deployForm.privatePort = String(v || '')
                      }}
                    />
                  </NFormItem>
                  <div class={styles.fullRow}>
                    {renderTextInput(
                      '私域管理员 Token',
                      this.deployForm.privateAdminToken,
                      (v) => {
                        this.deployForm.privateAdminToken = v
                      },
                      'password'
                    )}
                  </div>
                  <div class={styles.fullRow}>
                    {renderTextInput(
                      'Nginx 租户配置文件',
                      this.deployForm.nginxConfigFile,
                      (v) => {
                        this.deployForm.nginxConfigFile = v
                      }
                    )}
                  </div>
                  <div class={styles.fullRow}>
                    {renderTextInput(
                      'Nginx SSH 私钥',
                      this.deployForm.sshPrivateKey,
                      (v) => {
                        this.deployForm.sshPrivateKey = v
                      },
                      'textarea'
                    )}
                  </div>
                  <div class={styles.fullRow}>
                    {renderTextInput(
                      '自定义 Nginx 配置命令',
                      this.deployForm.nginxConfigCommand,
                      (v) => {
                        this.deployForm.nginxConfigCommand = v
                      },
                      'textarea',
                      '留空时自动生成标准租户转发配置，可使用 ${tenantCode}、${privateIp}、${privatePort}、${nginxConfigFile}'
                    )}
                  </div>
                  <div class={styles.fullRow}>
                    {renderTextInput(
                      'Nginx 重启命令',
                      this.deployForm.nginxReloadCommand,
                      (v) => {
                        this.deployForm.nginxReloadCommand = v
                      },
                      'textarea'
                    )}
                  </div>
                </div>
              </NForm>
            ) : (
              <NAlert type='info' showIcon={false}>
                当前账号不能配置私域，请联系管理员处理。
              </NAlert>
            )}
            <div class={styles.footer}>
              <NButton onClick={() => this.handleModalVisibleChange(false)}>
                取消
              </NButton>
              {this.canDeployPrivateDomain ? (
                <NButton
                  type='primary'
                  loading={this.deploying}
                  onClick={this.handleDeploy}
                >
                  保存并刷新 Nginx
                </NButton>
              ) : null}
            </div>
          </NSpace>
        </NModal>
      </>
    )
  }
})

export default DomainSwitcher
