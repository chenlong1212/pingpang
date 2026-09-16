<template>
  <div class="panel">
    <el-form :model="form" label-position="top" size="default">
      <el-row :gutter="8">
        <el-col :span="12"><el-form-item label="对手姓名"><el-input v-model="form.opponentName" placeholder="如：李四" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="比赛日期"><el-input v-model="form.matchDate" placeholder="2026-07-04" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="比赛类型"><el-select v-model="form.matchType" placeholder="选择" style="width:100%"><el-option v-for="t in types" :key="t" :label="t" :value="t" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="比分（我方在前）"><el-input v-model="form.matchScore" placeholder="如 3-1" /></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="备注（可选）"><el-input v-model="form.matchNote" type="textarea" :rows="2" placeholder="关键心得、技战术要点…" /></el-form-item></el-col>
      </el-row>
      <div class="btn-row">
        <el-button type="primary" :loading="saving" size="default" @click="save">
          {{ editingId ? '保存修改' : '保存比赛记录' }}
        </el-button>
        <el-button size="default" @click="openList">查看比赛记录</el-button>
        <el-button v-if="editingId" size="default" @click="cancelEdit">取消编辑</el-button>
      </div>
    </el-form>

    <!-- 悬浮查看比赛记录：倒序分页 -->
    <el-dialog v-model="listVisible" title="比赛记录" width="760px" append-to-body>
      <el-input v-model="keyword" placeholder="输入对手姓名筛选…" clearable style="margin-bottom:10px" @input="onKeywordInput" />
      <el-table :data="matches" size="small" max-height="380" v-loading="loading">
        <el-table-column label="#" width="55">
          <template #default="{ $index }">
            {{ (currentPage - 1) * pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="matchDate" label="日期" width="105" sortable :sort-orders="['descending']" :default-sort="{ prop: 'matchDate', order: 'descending' }" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ row.matchType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="opponentName" label="对手" show-overflow-tooltip />
        <el-table-column prop="matchScore" label="比分" width="70" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click.stop="editMatch(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click.stop="removeMatch(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top:10px; justify-content:flex-end"
        @current-change="load"
        @size-change="onSizeChange"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveMatch, updateMatch, deleteMatch, pageMatches } from '../api'

const types = ['开球网', '私下交流', '大型比赛']
const form = reactive({ opponentName: '', matchDate: '', matchType: '', matchScore: '', matchNote: '' })
const editingId = ref(null)
const saving = ref(false)

// 分页列表状态
const matches = ref([])
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const listVisible = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await pageMatches(currentPage.value, pageSize.value, keyword.value.trim() || undefined)
    matches.value = res.data.content || []
    total.value = res.data.totalElements || 0
    // 当前页超出总页数时（如删除后）回退
    const totalPages = res.data.totalPages || 1
    if (currentPage.value > totalPages && totalPages > 0) {
      currentPage.value = totalPages
      return load()
    }
  } catch (e) {
    ElMessage.error('加载失败：' + (e.message || e))
  } finally {
    loading.value = false
  }
}

function onKeywordInput() {
  currentPage.value = 1
  load()
}

function onSizeChange() {
  currentPage.value = 1
  load()
}

function openList() {
  listVisible.value = true
  load()
}

function editMatch(row) {
  form.opponentName = row.opponentName
  form.matchDate = row.matchDate
  form.matchType = row.matchType
  form.matchScore = row.matchScore
  form.matchNote = row.matchNote
  editingId.value = row.id
  listVisible.value = false
  ElMessage.info('正在编辑比赛记录，修改后点击「保存修改」')
}

function cancelEdit() {
  editingId.value = null
  form.opponentName = form.matchDate = form.matchType = form.matchScore = form.matchNote = ''
}

async function removeMatch(row) {
  try {
    await ElMessageBox.confirm(`确定删除与「${row.opponentName}」的这条比赛记录吗？`, '删除确认', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch (e) { return }
  try {
    const res = await deleteMatch(row.id)
    ElMessage.success(res.data)
    if (editingId.value === row.id) cancelEdit()
    load()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || e))
  }
}

async function save() {
  if (!form.opponentName || !form.matchDate || !form.matchType || !form.matchScore) {
    return ElMessage.warning('请完整填写对手、日期、类型、比分')
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateMatch(editingId.value, { ...form })
      ElMessage.success('比赛记录已更新')
    } else {
      await saveMatch({ ...form })
      ElMessage.success('比赛记录已保存')
    }
    cancelEdit()
    load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.btn-row { display: flex; gap: 8px; flex-wrap: wrap; }
</style>
