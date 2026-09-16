<template>
  <div class="panel">
    <!-- 处理模式切换：同步 vs Kafka 异步 -->
    <div class="mode-row">
      <span class="mode-label">入库方式</span>
      <el-radio-group v-model="asyncMode" size="small">
        <el-radio-button :value="false">同步入库</el-radio-button>
        <el-radio-button :value="true">Kafka 异步</el-radio-button>
      </el-radio-group>
      <el-tooltip placement="top" :content="asyncMode ? '接口立即返回，解析→切片→向量化→ES索引在消费端异步完成（可重试/死信队列）' : '接口内直接完成 解析→切片→向量化→ES索引，返回时已入库'" >
        <el-icon class="tip-icon"><QuestionFilled /></el-icon>
      </el-tooltip>
    </div>

    <el-tabs v-model="activeTab" size="small">
      <!-- Tab 1：文件上传 -->
      <el-tab-pane label="文件上传" name="file">
        <el-upload
          v-model:file-list="fileList"
          :auto-upload="false"
          :show-file-list="true"
          accept=".txt,.md,.pdf,.docx"
          :limit="1"
          :on-change="onFileChange"
          :on-exceed="handleExceed"
          :on-remove="clearFile"
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
    <el-dialog v-model="listVisible" title="知识库文档" width="680px" append-to-body>
      <el-table :data="docs" size="small" max-height="320" @row-click="row => viewDoc(row)" highlight-current-row>
        <el-table-column prop="docName" label="文档" show-overflow-tooltip />
        <el-table-column prop="docType" label="类型" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切片数" width="70" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click.stop="viewDoc(row)">查看</el-button>
            <el-button size="small" type="danger" link @click.stop="removeDoc(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="listVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 文档内容可视化查看 -->
    <el-dialog v-model="detailVisible" :title="detail.meta?.docName || '文档内容'" width="760px" append-to-body top="6vh">
      <div v-if="detail.loading" class="detail-loading">加载切片内容中…</div>
      <template v-else>
        <!-- 元信息 -->
        <div class="detail-meta">
          <el-tag size="small" :type="statusType(detail.meta?.status)" style="margin-right:8px">
            {{ statusText(detail.meta?.status) }}
          </el-tag>
          <span>类型：{{ detail.meta?.docType }}</span>
          <span>切片数：{{ detail.chunks?.length ?? 0 }}</span>
          <el-tag v-if="detail.meta?.status === 3" type="danger" size="small">失败原因：{{ detail.meta?.failMsg }}</el-tag>
        </div>

        <!-- 切片可视化 -->
        <div v-if="!detail.chunks?.length" class="detail-empty">
          <el-empty description="该文档没有可展示的切片内容" :image-size="60" />
        </div>
        <el-scrollbar height="46vh">
          <div v-for="(c, i) in detail.chunks" :key="i" class="chunk-card">
            <div class="chunk-head">
              <el-tag size="small" type="info" effect="plain">切片 #{{ c.chunkIndex }}</el-tag>
              <span class="chunk-src">{{ c.source === 'upload' ? '文件' : '文本录入' }}</span>
            </div>
            <div class="chunk-content">{{ c.content }}</div>
          </div>
        </el-scrollbar>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadDoc, uploadText as uploadTextApi, listDocs, getDocDetail, deleteDoc } from '../api'

const activeTab = ref('file')
const asyncMode = ref(true)
const file = ref(null)
const fileList = ref([])
const uploading = ref(false)
const docs = ref([])
const listVisible = ref(false)
const detailVisible = ref(false)
const detail = ref({ meta: null, chunks: [], loading: false })
const textForm = reactive({ title: '', content: '' })
let pollTimer = null

function onFileChange(f) { file.value = f.raw }
// 已有一个文件时再次选择：替换为新文件（否则 on-change 不触发导致一直提示未选文件）
function handleExceed(files) {
  fileList.value = [files[0]]
  file.value = files[0].raw
}
function clearFile() {
  file.value = null
  fileList.value = []
}
function statusText(s) { return ({ 0: '待处理', 1: '处理中', 2: '成功', 3: '失败' })[s] || s }
function statusType(s) { return ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' })[s] || 'info' }

async function load() {
  try {
    docs.value = (await listDocs()).data
  } catch (e) { /* 静默 */ }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    await load()
    const hasPending = docs.value.some(d => d.status === 1)
    if (!hasPending) stopPolling()
  }, 3000)
}
function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

function openList() {
  load()
  listVisible.value = true
}

async function viewDoc(row) {
  detailVisible.value = true
  detail.value = { meta: null, chunks: [], loading: true }
  try {
    detail.value = (await getDocDetail(row.docId)).data
    detail.value.loading = false
  } catch (e) {
    detail.value.loading = false
    ElMessage.error('加载文档内容失败：' + (e.message || e))
  }
}

async function removeDoc(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除文档「${row.docName}」吗？将同时清理 ES 索引切片与本地文件，不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    const res = await deleteDoc(row.docId)
    ElMessage.success(res.data)
    load()
    if (detailVisible.value && detail.value.meta?.docId === row.docId) {
      detailVisible.value = false
    }
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || e))
  }
}

async function upload() {
  if (!file.value) return ElMessage.warning('请先选择文件')
  uploading.value = true
  try {
    const res = await uploadDoc(file.value, asyncMode.value)
    if (asyncMode.value) {
      ElMessage.info(res.data)
      clearFile()
      startPolling()
    } else {
      ElMessage.success(res.data)
      clearFile()
      load()
    }
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
    const res = await uploadTextApi({ title: textForm.title.trim(), content: textForm.content.trim() }, asyncMode.value)
    if (asyncMode.value) {
      ElMessage.info(res.data)
      textForm.title = textForm.content = ''
      startPolling()
    } else {
      ElMessage.success(res.data)
      textForm.title = textForm.content = ''
      load()
    }
  } catch (e) {
    ElMessage.error('提交失败：' + (e.message || e))
  } finally {
    uploading.value = false
  }
}

onMounted(load)
onBeforeUnmount(stopPolling)
</script>

<style scoped>
.upload-icon { font-size: 32px; color: #0f766e; margin-bottom: 6px; }
.mode-row { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.mode-label { font-size: 13px; color: #475569; }
.tip-icon { color: #94a3b8; cursor: help; }
.detail-loading { text-align: center; padding: 40px 0; color: #64748b; }
.detail-meta { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; font-size: 13px; color: #475569; flex-wrap: wrap; }
.detail-empty { padding: 10px 0; }
.chunk-card {
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 10px 14px; margin-bottom: 10px;
}
.chunk-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.chunk-src { font-size: 12px; color: #94a3b8; }
.chunk-content {
  font-size: 13px; line-height: 1.8; color: #334155;
  white-space: pre-wrap; word-break: break-word;
}
</style>
