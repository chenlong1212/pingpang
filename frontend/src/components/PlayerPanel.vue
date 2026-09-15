<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-title">
        <el-icon><User /></el-icon>
        <span>选手档案</span>
      </div>
    </template>

    <el-form :model="form" label-position="top" size="default">
      <el-row :gutter="8">
        <el-col :span="12"><el-form-item label="姓名"><el-input v-model="form.playerName" placeholder="如：张三" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="持拍"><el-select v-model="form.handType" placeholder="请选择" style="width:100%"><el-option label="左手" value="左手" /><el-option label="右手" value="右手" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="握拍"><el-select v-model="form.gripType" placeholder="请选择" style="width:100%"><el-option label="直拍" value="直拍" /><el-option label="横拍" value="横拍" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="打法"><el-select v-model="form.playStyle" placeholder="请选择" style="width:100%"><el-option v-for="s in styles" :key="s" :label="s" :value="s" /></el-select></el-form-item></el-col>
      </el-row>
      <el-button type="primary" :loading="saving" @click="save">保存选手</el-button>
    </el-form>

    <el-divider />
    <el-input v-model="keyword" placeholder="搜索姓名…" clearable style="margin-bottom:10px" />
    <el-table :data="filtered" size="small" max-height="240">
      <el-table-column prop="playerName" label="姓名" />
      <el-table-column prop="handType" label="持拍" />
      <el-table-column prop="gripType" label="握拍" />
      <el-table-column label="打法">
        <template #default="{ row }">
          <el-tag size="small" type="success">{{ row.playStyle || '-' }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { savePlayer, listPlayers } from '../api'

const styles = ['反胶', '长胶', '生胶', '推挡', '防弧']
const form = reactive({ playerName: '', handType: '', gripType: '', playStyle: '' })
const saving = ref(false)
const players = ref([])
const keyword = ref('')

const filtered = computed(() => {
  const kw = keyword.value.trim()
  return kw ? players.value.filter(p => (p.playerName || '').includes(kw)) : players.value
})

async function load() {
  try {
    players.value = (await listPlayers()).data
  } catch (e) { /* 后端未启动时静默 */ }
}

async function save() {
  if (!form.playerName) return ElMessage.warning('请填写姓名')
  saving.value = true
  try {
    await savePlayer({ ...form })
    ElMessage.success('选手已保存')
    form.playerName = form.handType = form.gripType = form.playStyle = ''
    load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
