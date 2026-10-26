<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as Icons from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { menuRoutes } from '@/router'
import { session, setAdmin, clearSession, isSuperAdmin } from '@/utils/auth'
import { ADMIN_ROLE } from '@/utils/dict'
import { getMe } from '@/api'
import PasswordDialog from './PasswordDialog.vue'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const pwdVisible = ref(false)

/** 按 meta.group 分组；没有分组的放顶层 */
const menu = computed(() => {
  const out = []
  const groups = {}
  menuRoutes
    .filter((r) => !r.meta.superOnly || isSuperAdmin())
    .forEach((r) => {
      const item = { path: `/${r.path}`, title: r.meta.title, icon: Icons[r.meta.icon] }
      if (!r.meta.group) return out.push(item)
      if (!groups[r.meta.group]) {
        groups[r.meta.group] = { group: r.meta.group, children: [] }
        out.push(groups[r.meta.group])
      }
      groups[r.meta.group].children.push(item)
    })
  return out
})

const activePath = computed(() => `/${route.path.split('/')[1] || ''}`)

onMounted(async () => {
  try {
    const me = await getMe()
    if (me) setAdmin(me)
  } catch {
    /* 401 已由请求拦截器处理 */
  }
})

async function logout() {
  await ElMessageBox.confirm('确定退出登录吗？', '退出', { type: 'warning' })
  clearSession()
  router.replace({ name: 'login' })
}

function onCommand(cmd) {
  if (cmd === 'password') pwdVisible.value = true
  else if (cmd === 'logout') logout().catch(() => {})
}
</script>

<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '208px'" class="aside">
      <div class="brand">
        <span class="logo">粮</span>
        <span v-show="!collapsed" class="brand-text">粮满满 · 运营后台</span>
      </div>
      <el-scrollbar>
        <el-menu :default-active="activePath" :collapse="collapsed" :collapse-transition="false" router class="menu">
          <template v-for="m in menu" :key="m.path || m.group">
            <el-menu-item v-if="m.path" :index="m.path">
              <el-icon><component :is="m.icon" /></el-icon>
              <template #title>{{ m.title }}</template>
            </el-menu-item>
            <el-menu-item-group v-else>
              <template #title>
                <span v-show="!collapsed">{{ m.group }}</span>
              </template>
              <el-menu-item v-for="c in m.children" :key="c.path" :index="c.path">
                <el-icon><component :is="c.icon" /></el-icon>
                <template #title>{{ c.title }}</template>
              </el-menu-item>
            </el-menu-item-group>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container class="main-wrap">
      <el-header class="header">
        <div class="header-left">
          <el-button text :icon="collapsed ? Icons.Expand : Icons.Fold" @click="collapsed = !collapsed" />
          <span class="page-title">{{ route.meta.title }}</span>
        </div>
        <el-dropdown trigger="click" @command="onCommand">
          <span class="admin">
            <el-avatar :size="28" class="avatar">{{ (session.admin?.name || session.admin?.username || '管')[0] }}</el-avatar>
            <span>{{ session.admin?.name || session.admin?.username || '管理员' }}</span>
            <el-tag v-if="session.admin?.role" size="small" :type="ADMIN_ROLE.type[session.admin.role]" disable-transitions>
              {{ ADMIN_ROLE.label[session.admin.role] || session.admin.role }}
            </el-tag>
            <el-icon><Icons.ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password" :icon="Icons.Lock">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" :icon="Icons.SwitchButton" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <PasswordDialog v-model="pwdVisible" />
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: #fff;
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  transition: width 0.2s;
  overflow: hidden;
}
.brand {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}
.logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--lm-brand);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.brand-text {
  font-weight: 600;
  white-space: nowrap;
}
.menu {
  border-right: none;
}
.menu :deep(.el-menu-item-group__title) {
  padding-top: 14px;
  font-size: 12px;
}
.menu :deep(.el-menu-item) {
  height: 44px;
}
.menu :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary-light-9);
  font-weight: 600;
}
.main-wrap {
  min-width: 0;
}
.header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 8px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.admin {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.avatar {
  background: var(--lm-brand);
}
.main {
  padding: 16px;
  overflow-y: auto;
}
</style>
