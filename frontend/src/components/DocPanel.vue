<template>
  <div class="panel">
    <el-tabs v-model="activeTab" size="small">
      <!-- Tab 1：文件上传 -->
      <el-tab-pane label="文件上传" name="file">
        <el-upload
          :auto-upload="false"
          :show-file-list="true"
          accept=".txt,.md,.pdf,.docx"
          :limit="1"
          :on-change="onFileChange"
          :on-remove="() => file = null"
          drag
          style="margin-bottom:10px"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div>拖拽 / 点击选择文件</div>
          <template #tip>
            <div class="el-upload__tip">txt / md / pdf / docx，自动解析切片向量化</div>
          </template>
        </el-upload>
        <el-button type="primary" size="default" :loading="uploading" @click="upload">上传并入库</el-button>
      </el-tab-pane>

      <!-- Tab 2：文本录入 -->
      <el-tab-pane label="文本录入" name="text">
        <el-form label-position="top" size="small">
          <el-form-item label="标题">
            <el-input v-model="textForm.title" placeholder="如：长胶应对心得" />
          </el-form-item>
          <el-form-item label="知识内容">
            <el-input
              v-model="textForm.content"
              type="textarea"
              :rows="5"
              placeholder="粘贴或输入乒乓球知识、战术笔记…"
            />
          </el-form-item>
          <el-button type="primary" size="default" :loading="uploading" @click="uploadText">提交入库</el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <el-divider style="margin:10px 0" />
    <el-button size="default" @click="openList">查看文档库</el-button>

    <!-- 悬浮查看文档列表 -->
    <el-dialog v-model="listVisible" title="知识库文档" width="560px" append-to-body>
      <el-table :data="docs" size="small" max-height="320">
        <el-table-column prop="docName" label="文档" show-overflow-tooltip />
        <el-table-column prop="docType" label="类型" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切片数" width="70" />
      </el-table>
      <template #footer>
        <el-button @click="listVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadDoc, uploadText as uploadTextApi, listDocs } from '../api'

const activeTab = ref('file')
const file = ref(null)
const uploading = ref(false)
const docs = ref([])
const listVisible = ref(false)
const textForm = reactive({ title: '', content: '' })

function onFileChange(f) { file.value = f.raw }
function statusText(s) { return ({ 0: '待处理', 1: '处理中', 2: '成功', 3: '失败' })[s] || s }
function statusType(s) { return ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' })[s] || 'info' }

async function load() {
  try {
    docs.value = (await listDocs()).data
  } catch (e) { /* 静默 */ }
}

function openList() {
  load()
  listVisible.value = true
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

async function uploadText() {
  if (!textForm.title.trim()) return ElMessage.warning('请填写标题')
  if (!textForm.content.trim()) return ElMessage.warning('请填写知识内容')
  uploading.value = true
  try {
    const res = await uploadTextApi({ title: textForm.title.trim(), content: textForm.content.trim() })
    ElMessage.success(res.data)
    textForm.title = textForm.content = ''
    load()
  } catch (e) {
    ElMessage.error('提交失败：' + (e.message || e))
  } finally {
    uploading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.upload-icon { font-size: 32px; color: #0f766e; margin-bottom: 6px; }
</style>
