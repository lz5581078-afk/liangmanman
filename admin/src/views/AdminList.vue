<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listAdmins, createAdmin, resetAdminPassword, toggleAdmin } from '@/api'
import { session } from '@/utils/auth'
import { ADMIN_ROLE } from '@/utils/dict'
import { toList, time, text } from '@/utils/format'
import { confirmAction } from '@/utils/prompt'
import StatusTag from '@/components/StatusTag.vue'

const loading = ref(false)
const admins = ref([])

async function load() {
  loading.value = true
  try {
    admins.value = toList(await listAdmins())
  } catch {
    admins.value = []
  } finally {
    loading.value = false
  }
}

const EMPTY = { username: '', password: '', name: '', role: 'OPERATOR' }
const dialog = reactive({ visible: false, saving: false })
const form = reactive({ ...EMPTY })
const formRef = ref()
const rules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_.-]{3,32}$/, message: '3–32 位字母、数字、下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 32, message: '6–32 位', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

function openCreate() {
  Object.assign(form, EMPTY)
  dialog.visible = true
  formRef.value?.clearValidate()
}

async function save() {
  await formRef.value.validate()
  dialog.saving = true
  try {
    await createAdmin({ ...form, username: form.username.trim(), name: form.name.trim() })
    ElMessage.success('已创建')
    dialog.visible = false
    load()
  } finally {
    dialog.saving = false
  }
}

async function resetPwd(row) {
  try {
    const { value } = await ElMessageBox.prompt(`为「${row.name || row.username}」设置新密码`, '重置密码', {
      inputType: 'password',
      inputPlaceholder: '6–32 位新密码',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValidator: (v) => (v && v.length >= 6 && v.length <= 32 ? true : '6–32 位'),
      closeOnClickModal: false
    })
    await resetAdminPassword(row.id, value)
  } catch {
    return
  }
  ElMessage.success('密码已重置，请告知对方')
}

async function toggle(row) {
  const enabling = !isEnabled(row)
  try {
    await confirmAction(`确定${enabling ? '启用' : '停用'}「${row.name || row.username}」？${enabling ? '' : '停用后该账号无法登录。'}`)
    await toggleAdmin(row.id)
  } catch {
    return
  }
  ElMessage.success(enabling ? '已启用' : '已停用')
  load()
}

/** 兼容 enabled / disabled / status 三种表示 */
const isEnabled = (row) => {
  if (row.enabled !== undefined) return !!row.enabled
  if (row.disabled !== undefined) return !row.disabled
  if (row.status !== undefined) return row.status !== 'DISABLED'
  return true
}
const isSelf = (row) => session.admin && row.id === session.admin.id

onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="muted">超级管理员可管理管理员账号；运营可使用除本页外的全部功能。</span>
      <el-button type="success" :icon="Plus" @click="openCreate">新增管理员</el-button>
    </div>

    <el-table v-loading="loading" :data="admins" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="账号" min-width="140">
        <template #default="{ row }">
          {{ row.username }}
          <el-tag v-if="isSelf(row)" size="small" type="info" disable-transitions>我</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="姓名" min-width="120">
        <template #default="{ row }">{{ text(row.name) }}</template>
      </el-table-column>
      <el-table-column label="角色" width="120">
        <template #default="{ row }"><StatusTag :dict="ADMIN_ROLE" :value="row.role" /></template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="isEnabled(row) ? 'success' : 'info'" size="small" disable-transitions>{{ isEnabled(row) ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" width="170">
        <template #default="{ row }">{{ time(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="resetPwd(row)">重置密码</el-button>
          <el-button v-if="!isSelf(row)" link :type="isEnabled(row) ? 'danger' : 'success'" @click="toggle(row)">
            {{ isEnabled(row) ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" title="新增管理员" width="440px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="登录用，字母数字" autocomplete="off" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="20" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="form.role">
            <el-radio v-for="o in ADMIN_ROLE.options" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
