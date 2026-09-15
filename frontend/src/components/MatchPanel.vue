<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-title">
        <el-icon><Trophy /></el-icon>
        <span>比赛记录</span>
      </div>
    </template>

    <el-form :model="form" label-position="top" size="default">
      <el-row :gutter="8">
        <el-col :span="12"><el-form-item label="对手姓名"><el-input v-model="form.opponentName" placeholder="如：李四" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="比赛日期"><el-date-picker v-model="form.matchDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="比赛类型"><el-select v-model="form.matchType" placeholder="请选择" style="width:100%"><el-option v-for="t in types" :key="t" :label="t" :value="t" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="比分（我方在前）"><el-select v-model="form.matchScore" placeholder="请选择" style="width:100%"><el-option v-for="s in scores" :key="s" :label="s" :value="s" /></el-select></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="备注（可选）"><el-input v-model="form.matchNote" type="textarea" :rows="2" placeholder="关键心得、技战术要点…" /></el-form-item></el-col>
      </el-row>
      <el-button type="primary" :loading="saving" @click="save">保存比赛记录</el-button>
    </el-form>

    <el-divider />
    <el-input v-model="keyword" placeholder="搜索对手…" clearable style="margin-bottom:10px" />
    <el-table :data="filtered" size="small" max-height="240">
      <el-table-column prop="opponentName" label="对手" />
      <el-table-column prop="matchDate" label="日期" width="110" />
      <el-table-column label="类型">
        <template #default="{ row }">
          <el-tag size="small" type="primary">{{ row.matchType || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="matchScore" label="比分" width="70" />
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { saveMatch, listMatches } from '../api'

const types = ['开球网', '私下交流', '大型比赛']
const scores = ['2-0', '3-0', '3-1', '3-2']
const form = reactive({ opponentName: '', matchDate: '', matchType: '', matchScore: '', matchNote: '' })
const saving = ref(false)
const matches = ref([])
const keyword = ref('')

const filtered = computed(() => {
  const kw = keyword.value.trim()
  return kw ? matches.value.filter(m => (m.opponentName || '').includes(kw)) : matches.value
})

async function load() {
  try {
    matches.value = (await listMatches()).data
  } catch (e) { /* 静默 */ }
}

async function save() {
  if (!form.opponentName || !form.matchDate || !form.matchType || !form.matchScore) {
    return ElMessage.warning('请完整填写对手、日期、类型、比分')
  }
  saving.value = true
  try {
    await saveMatch({ ...form })
    ElMessage.success('比赛记录已保存')
    form.opponentName = form.matchDate = form.matchType = form.matchScore = form.matchNote = ''
    load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
