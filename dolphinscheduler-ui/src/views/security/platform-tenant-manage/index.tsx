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

import { defineComponent, onMounted, reactive, ref, toRefs } from 'vue'
import {
  NButton,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NPagination,
  NPopconfirm,
  NSpace
} from 'naive-ui'
import Card from '@/components/card'
import Modal from '@/components/modal'
import Search from '@/components/input-search'
import {
  createPlatformTenant,
  deletePlatformTenantById,
  queryPlatformTenantListPaging,
  updatePlatformTenant
} from '@/service/modules/platform-tenants'

interface PlatformTenantRow {
  id: number
  tenantCode: string
  tenantName: string
  description?: string
  createTime?: string
  updateTime?: string
}

const PlatformTenantManage = defineComponent({
  name: 'platform-tenant-manage',
  setup() {
    const formRef = ref()
    const state = reactive({
      loading: false,
      showModal: false,
      page: 1,
      pageSize: 10,
      totalPage: 1,
      searchVal: '',
      tableData: [] as PlatformTenantRow[],
      currentRow: null as PlatformTenantRow | null,
      formData: {
        tenantCode: '',
        tenantName: '',
        description: ''
      }
    })

    const rules = {
      tenantCode: {
        required: true,
        trigger: ['input', 'blur'],
        message: 'Please enter tenant code'
      },
      tenantName: {
        required: true,
        trigger: ['input', 'blur'],
        message: 'Please enter tenant name'
      }
    }

    const requestData = async () => {
      state.loading = true
      const res = await queryPlatformTenantListPaging({
        pageNo: state.page,
        pageSize: state.pageSize,
        searchVal: state.searchVal
      })
      state.tableData = res.totalList || []
      state.totalPage = res.totalPage || 1
      state.loading = false
    }

    const openModal = (row?: PlatformTenantRow) => {
      state.currentRow = row || null
      state.formData = row
        ? {
            tenantCode: row.tenantCode,
            tenantName: row.tenantName,
            description: row.description || ''
          }
        : {
            tenantCode: '',
            tenantName: '',
            description: ''
          }
      state.showModal = true
    }

    const closeModal = () => {
      state.showModal = false
    }

    const confirmModal = async () => {
      await formRef.value?.validate()
      if (state.currentRow) {
        await updatePlatformTenant(state.formData, { id: state.currentRow.id })
      } else {
        await createPlatformTenant(state.formData)
      }
      closeModal()
      requestData()
    }

    const deleteRow = async (row: PlatformTenantRow) => {
      await deletePlatformTenantById(row.id)
      requestData()
    }

    const handleSearch = () => {
      state.page = 1
      requestData()
    }

    const columns = [
      { title: 'Code', key: 'tenantCode' },
      { title: 'Name', key: 'tenantName' },
      { title: 'Description', key: 'description' },
      { title: 'Create Time', key: 'createTime' },
      { title: 'Update Time', key: 'updateTime' },
      {
        title: 'Actions',
        key: 'actions',
        render: (row: PlatformTenantRow) => (
          <NSpace>
            <NButton size='small' onClick={() => openModal(row)}>
              Edit
            </NButton>
            <NPopconfirm onPositiveClick={() => deleteRow(row)}>
              {{
                trigger: () => <NButton size='small'>Delete</NButton>,
                default: () => 'Confirm delete?'
              }}
            </NPopconfirm>
          </NSpace>
        )
      }
    ]

    onMounted(requestData)

    return {
      ...toRefs(state),
      formRef,
      rules,
      columns,
      requestData,
      openModal,
      closeModal,
      confirmModal,
      handleSearch
    }
  },
  render() {
    return (
      <NSpace vertical>
        <Card>
          <NSpace justify='space-between'>
            <NButton
              size='small'
              type='primary'
              onClick={() => this.openModal()}
            >
              Create
            </NButton>
            <Search
              v-model:value={this.searchVal}
              placeholder='Search'
              onSearch={this.handleSearch}
            />
          </NSpace>
        </Card>
        <Card title='Platform Tenant Manage'>
          <NSpace vertical>
            <NDataTable
              loading={this.loading}
              columns={this.columns}
              data={this.tableData}
              row-class-name='items'
            />
            <NSpace justify='center'>
              <NPagination
                v-model:page={this.page}
                v-model:page-size={this.pageSize}
                page-count={this.totalPage}
                show-size-picker
                page-sizes={[10, 30, 50]}
                show-quick-jumper
                onUpdatePage={this.requestData}
                onUpdatePageSize={this.requestData}
              />
            </NSpace>
          </NSpace>
        </Card>
        <Modal
          show={this.showModal}
          title={this.currentRow ? 'Edit' : 'Create'}
          onCancel={this.closeModal}
          onConfirm={this.confirmModal}
        >
          <NForm
            ref='formRef'
            model={this.formData}
            rules={this.rules}
            labelPlacement='left'
            labelWidth={120}
          >
            <NFormItem label='Code' path='tenantCode'>
              <NInput v-model:value={this.formData.tenantCode} />
            </NFormItem>
            <NFormItem label='Name' path='tenantName'>
              <NInput v-model:value={this.formData.tenantName} />
            </NFormItem>
            <NFormItem label='Description' path='description'>
              <NInput
                v-model:value={this.formData.description}
                type='textarea'
              />
            </NFormItem>
          </NForm>
        </Modal>
      </NSpace>
    )
  }
})

export default PlatformTenantManage
