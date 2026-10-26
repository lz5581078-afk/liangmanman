<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listStandardProducts, createStandardProduct, updateStandardProduct, deleteStandardProduct,
  verifyStandardProduct, mergeStandardProduct
} from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { useCategories } from '@/composables/useCategories'
import { time, text } from '@/utils/format'
import { confirmAction } from '@/utils/prompt'
import FilterBar from '@/components/FilterBar.vue'
import Pager from '@/components/Pager.vue'
import Thumb from '@/components/Thumb.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import StandardPicker from './StandardPicker.vue'

/**
 * 商品库：每一条是具体的一款（品牌 + 名称 + 规格，有登记证号 / 条码）。
 * 各店卖的同一款挂到这里，买家才能“同款比价”。
 * 商家上架时拍包装认出条码或登记证号、库里没有的，会自动建一条“待核实”，在这里核实或合并。
 */
const { categories, nameOf } = useCategories()
const { query, records, total, loading, page, pageSize, load, search, reset } = usePagedList(listStandardProducts, {
  keyword: '',
  categoryId: '',
  status: ''
})

function setStatus(s) {
  query.status = s
  search()
}

const EMPTY = { categoryId: null, brand: '', name: '', spec: '', regNo: '', barcode: '', manufacturer: '', image: '', keywords: '' }
const dialog = reactive({ visible: false, id: null, saving: false, pending: false })
const form = reactive({ ...EMPTY })
const formRef = ref()
const rules = {
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  barcode: [{ pattern: /^\d{8,14}$/, message: '条码是 8 到 14 位数字', trigger: 'blur' }]
}

function openEdit(row) {
  dialog.id = row?.id || null
  dialog.pending = row?.status === 'PENDING'
  Object.assign(form, EMPTY)
  if (row) Object.keys(EMPTY).forEach((k) => (form[k] = row[k] ?? EMPTY[k]))
  dialog.visible = true
  formRef.value?.clearValidate()
}

async function save() {
  await formRef.value.validate()
  dialog.saving = true
  const body = { ...form }
  Object.keys(body).forEach((k) => typeof body[k] === 'string' && (body[k] = body[k].trim()))
  try {
    if (dialog.id) await updateStandardProduct(dialog.id, body)
    else await createStandardProduct(body)
    ElMessage.success(dialog.pending ? '已保存并核实' : '已保存')
    dialog.visible = false
    load()
  } finally {
    dialog.saving = false
  }
}

async function verify(row) {
  await verifyStandardProduct(row.id)
  ElMessage.success('已核实')
  load()
}

async function remove(row) {
  try {
    await confirmAction(`确定删除「${row.displayName}」？还有商品挂在它上面时不能删，可以用“合并”。`, '删除')
    await deleteStandardProduct(row.id)
  } catch {
    return
  }
  ElMessage.success('已删除')
  load()
}

// 合并：同一款被建了两条
const merge = reactive({ visible: false, from: null, submitting: false })
function openMerge(row) {
  merge.from = row
  merge.visible = true
}
async function doMerge(into) {
  if (!into || !merge.from) return
  try {
    await confirmAction(
      `把「${merge.from.displayName}」并入「${into.displayName}」？\n它下面的 ${merge.from.productCount || 0} 个商品会改挂到后者，这一条会删除。`,
      '合并'
    )
  } catch {
    return
  }
  merge.submitting = true
  try {
    await mergeStandardProduct(merge.from.id, into.id)
    ElMessage.success('已合并')
    merge.visible = false
    load()
  } finally {
    merge.submitting = false
  }
}

const route = useRoute()
onMounted(() => {
  if (typeof route.query.status === 'string') query.status = route.query.status
  load()
})
</script>

<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" class="tip">
      每一条是具体的一款：同一个牌子、同一个规格。条码相同、或登记证号和规格都相同，就是同一款。
      各店卖的同一款挂到这里，农户才能比价。商家上架时自动建的会标“待核实”，看一下照片和信息，没问题点“核实”；同一款建重了用“合并”。
    </el-alert>

    <el-radio-group :model-value="query.status" class="tabs" @change="setStatus">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button value="PENDING">待核实</el-radio-button>
      <el-radio-button value="VERIFIED">已核实</el-radio-button>
    </el-radio-group>

    <FilterBar @search="search" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="名称 / 品牌 / 登记证号 / 条码" clearable />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="query.categoryId" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <template #extra>
        <el-button type="success" :icon="Plus" @click="openEdit(null)">新增一款</el-button>
      </template>
    </FilterBar>

    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="图片" width="76">
        <template #default="{ row }"><Thumb :src="row.image" :size="48" /></template>
      </el-table-column>
      <el-table-column label="款" min-width="220">
        <template #default="{ row }">
          <div>
            <b>{{ row.displayName }}</b>
            <el-tag v-if="row.status === 'PENDING'" size="small" type="warning" class="ml">待核实</el-tag>
          </div>
          <div class="muted">{{ nameOf[row.categoryId] || '未分类' }}<template v-if="row.manufacturer"> · {{ row.manufacturer }}</template></div>
        </template>
      </el-table-column>
      <el-table-column label="登记证号 / 条码" min-width="190">
        <template #default="{ row }">
          <div>{{ text(row.regNo) }}</div>
          <div class="muted">{{ row.barcode || '无条码' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="在卖" width="80" align="right">
        <template #default="{ row }">{{ row.productCount || 0 }} 个</template>
      </el-table-column>
      <el-table-column label="来源" width="150">
        <template #default="{ row }">
          <span v-if="row.sourceStoreName" class="muted">{{ row.sourceStoreName }} 上架时自动建</span>
          <span v-else class="muted">运营录入</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="140">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link type="success" @click="verify(row)">核实</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="openMerge(row)">合并</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑这一款' : '新增一款'" width="580px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="form.brand" placeholder="包装上的品牌" maxlength="32" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：复合肥 15-15-15" maxlength="64" />
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="form.spec" placeholder="如：50kg、100g、1L" maxlength="32" />
        </el-form-item>
        <el-form-item label="登记证号">
          <el-input v-model="form.regNo" placeholder="农药 PD…、肥料 …准字…号、种子审定编号" maxlength="64" />
        </el-form-item>
        <el-form-item label="条码" prop="barcode">
          <el-input v-model="form.barcode" placeholder="包装上的商品条码，69 开头 13 位" maxlength="14" />
        </el-form-item>
        <el-form-item label="生产企业">
          <el-input v-model="form.manufacturer" maxlength="64" />
        </el-form-item>
        <el-form-item label="搜索关键词">
          <el-input v-model="form.keywords" type="textarea" :rows="2" placeholder="这一款特有的叫法，空格分隔。通用叫法请加到「搜索同义词」" />
        </el-form-item>
        <el-form-item label="图片">
          <ImageUpload v-model="form.image" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">{{ dialog.pending ? '保存并核实' : '保存' }}</el-button>
      </template>
    </el-dialog>

    <StandardPicker
      v-model="merge.visible"
      :product="merge.from ? { name: merge.from.displayName, images: merge.from.image, storeName: '要并掉的这一条' } : null"
      :exclude-id="merge.from?.id"
      :submitting="merge.submitting"
      title="合并到另一款"
      confirm-text="并入这一款"
      @select="doMerge"
    />
  </div>
</template>

<style scoped>
.tip {
  margin-bottom: 14px;
}
.tabs {
  margin-bottom: 12px;
}
.ml {
  margin-left: 6px;
}
</style>
