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

import { computed, defineComponent, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NSelect } from 'naive-ui'
import { useUserStore } from '@/store/user/user'
import type { ProjectList } from '@/service/modules/projects/types'
import { goToProject, queryProjects } from './use-project-navigation'
import styles from './index.module.scss'

const ProjectSwitcher = defineComponent({
  name: 'ProjectSwitcher',
  setup() {
    const route = useRoute()
    const router = useRouter()
    const { t } = useI18n()
    const userStore = useUserStore()
    const loading = ref(false)
    const projects = ref<ProjectList[]>([])

    const routeProjectCode = computed(() => {
      const code = route.params.projectCode
      if (Array.isArray(code)) {
        return code[0] || null
      }
      return code ? String(code) : null
    })

    const options = computed(() =>
      projects.value.map((project) => ({
        label: project.name,
        value: String(project.code)
      }))
    )

    const refreshProjects = async () => {
      if (loading.value) return
      loading.value = true
      try {
        projects.value = await queryProjects()
      } finally {
        loading.value = false
      }
    }

    const findProject = (projectCode: string) =>
      projects.value.find((project) => String(project.code) === projectCode)

    const handleChange = async (projectCode: string) => {
      let project = findProject(projectCode)
      if (!project) {
        await refreshProjects()
        project = findProject(projectCode)
      }
      if (project) {
        await goToProject(router, project)
      }
    }

    onMounted(refreshProjects)

    watch(
      () => userStore.getPlatformTenantId,
      async () => {
        projects.value = []
        await refreshProjects()
      }
    )

    watch(
      () => userStore.getDomainMode,
      async () => {
        projects.value = []
        await refreshProjects()
      }
    )

    watch(
      () => routeProjectCode.value,
      async (projectCode) => {
        if (projectCode && !findProject(projectCode)) {
          await refreshProjects()
        }
      }
    )

    return {
      t,
      options,
      loading,
      routeProjectCode,
      refreshProjects,
      handleChange
    }
  },
  render() {
    if (this.options.length <= 0 && !this.loading) return null
    return (
      <NSelect
        class={styles.switcher}
        size='small'
        value={this.routeProjectCode}
        options={this.options}
        filterable
        clearable={false}
        loading={this.loading}
        placeholder={this.t('menu.project')}
        onFocus={this.refreshProjects}
        onUpdateValue={this.handleChange}
      />
    )
  }
})

export default ProjectSwitcher
