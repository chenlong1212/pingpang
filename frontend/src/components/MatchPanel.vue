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

    <!-- 悬浮查看比赛记录 -->
    <el-dialog v-model="listVisible" title="比赛记录" width="720px" append-to-body>
      <el-input v-model="keyword" placeholder="输入对手姓名筛选…" clearable style="margin-bottom:10px" />
      <el-table :data="filtered" size="small" max-height="340">
        <el-table-column prop="opponentName" label="对手" />
        <el-table-column prop="matchDate" label="日期" width="110" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ row.matchType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="matchScore" label="比分" width="80" />
        <el-table-column prop="matchNote" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click.stop="editMatch(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click.stop="removeMatch(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="listVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveMatch, updateMatch, deleteMatch, listMatches } from '../api'

const types = ['开球网', '私下交流', '大型比赛']
const form = reactive({ opponentName: '', matchDate: '', matchType: '', matchScore: '', matchNote: '' })
const editingId = ref(null)
const saving = ref(false)
const matches = ref([])
const keyword = ref('')
const listVisible = ref(false)

const filtered = computed(() => {
  const kw = keyword.value.trim()
  return kw ? matches.value.filter(m => (m.opponentName || '').includes(kw)) : matches.value
})

async function load() {
  try {
    matches.value = (await listMatches()).data
  } catch (e) { /* 静默 */ }
}

function openList() {
  keyword.value = ''
  load()
  listVisible.value = true
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
    load()
    if (editingId.value === row.id) cancelEdit()
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

onMounted(load)
</script>

<style scoped>
.btn-row { display: flex; gap: 8px; flex-wrap: wrap; }
</style>
