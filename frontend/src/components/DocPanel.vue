<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-title">
        <el-icon><FolderOpened /></el-icon>
        <span>知识库文档（txt/md → 切片向量化 → ES）</span>
      </div>
    </template>

    <el-upload
      :auto-upload="false"
      :show-file-list="true"
      accept=".txt,.md"
      :limit="1"
      :on-change="onFileChange"
      :on-remove="() => file = null"
      drag
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div>拖拽文件到此处，或点击选择</div>
      <template #tip>
        <div class="el-upload__tip">支持 txt / md 文本文件，上传后自动切片并向量化入库</div>
      </template>
    </el-upload>

    <el-button type="primary" :loading="uploading" style="margin-top:12px" @click="upload">
      上传并入库
    </el-button>

    <el-divider />
    <el-table :data="docs" size="small" max-height="200">
      <el-table-column prop="docName" label="文档" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="切片数" width="70" />
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadDoc, listDocs } from '../api'

const file = ref(null)
const uploading = ref(false)
const docs = ref([])

function onFileChange(f) { file.value = f.raw }
function statusText(s) { return ({ 0: '待处理', 1: '处理中', 2: '成功', 3: '失败' })[s] || s }
function statusType(s) { return ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' })[s] || 'info' }

async function load() {
  try {
    docs.value = (await listDocs()).data
  } catch (e) { /* 静默 */ }
}

async function upload() {
  if (!file.value) return ElMessage.warning('请先选择文件')
  uploading.value = true
  try {
    const res = await uploadDoc(file.value)
    ElMessage.success(res.data)
    file.value = null
    load()
  } catch (e) {
    ElMessage.error('上传失败：' + (e.message || e))
  } finally {
    uploading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.upload-icon { font-size: 40px; color: #0f766e; margin-bottom: 8px; }
</style>
