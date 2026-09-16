<template>
  <el-card shadow="never" class="chat-card">
    <template #header>
      <div class="card-title">
        <el-icon><ChatDotRound /></el-icon>
        <span>对话问答（Agent 自动选择工具）</span>
      </div>
    </template>

    <div class="chat-layout">
      <!-- 左侧：固定会话列表 -->
      <div class="session-side">
        <el-button size="small" type="primary" class="new-btn" :icon="Plus" @click="newSession">新建会话</el-button>
        <div class="session-list">
          <div
            v-for="s in sessions"
            :key="s.sessionId"
            class="session-item"
            :class="{ active: s.sessionId === currentSessionId }"
            @click="switchSession(s.sessionId)"
          >
            <span class="session-title" :title="s.title">{{ s.title }}</span>
            <el-icon class="del-icon" @click.stop="removeSession(s)"><Close /></el-icon>
          </div>
          <div v-if="!sessions.length" class="session-empty">暂无会话</div>
        </div>
      </div>

      <!-- 右侧：聊天区 -->
      <div class="chat-main">
        <div ref="chatBox" class="chat-box">
          <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
            <div class="avatar">{{ m.role === 'user' ? '我' : 'AI' }}</div>
            <div class="bubble" v-html="renderMarkdown(m.content)"></div>
          </div>
          <el-empty v-if="!messages.length" description="问点什么吧，例如：张三的打法是什么" :image-size="60" />
        </div>

        <div class="input-row">
          <el-input
            v-model="question"
            placeholder="试试问：张三的打法 / 我和李四的比赛 / 长胶怎么应对"
            clearable
            @keyup.enter="send"
          />
          <el-button type="primary" :loading="loading" @click="send">发送</el-button>
        </div>
        <div class="suggest">
          <el-tag v-for="s in suggests" :key="s" size="small" class="suggest-tag" @click="fill(s)">{{ s }}</el-tag>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Close } from '@element-plus/icons-vue'
import { chatAsk, chatHistory, chatSessions, createSession, deleteSession } from '../api'

const messages = ref([])
const question = ref('')
const loading = ref(false)
const chatBox = ref(null)
const suggests = ['张三的打法是什么', '我和李四的比赛记录', '长胶怎么应对']

// 会话列表 & 当前会话（localStorage 持久化）
const sessions = ref([])
const currentSessionId = ref(localStorage.getItem('pp_session_id') || '')
const persistSession = () => localStorage.setItem('pp_session_id', currentSessionId.value)

async function refreshSessions() {
  try {
    const res = await chatSessions()
    sessions.value = res.data || []
    // 若当前会话不在列表（如被删/新建），回落到最近一个
    if (currentSessionId.value && !sessions.value.find(s => s.sessionId === currentSessionId.value)) {
      currentSessionId.value = ''
    }
    if (!currentSessionId.value && sessions.value.length) {
      currentSessionId.value = sessions.value[0].sessionId
      persistSession()
      await loadHistory()
    }
  } catch (e) { /* 后端未启动 */ }
}

async function loadHistory() {
  if (!currentSessionId.value) {
    messages.value = []
    return
  }
  try {
    const res = await chatHistory(currentSessionId.value)
    messages.value = (res.data || []).map(m => ({
      role: m.role === 'user' ? 'user' : 'ai',
      content: m.content || ''
    }))
    scrollToBottom()
  } catch (e) {
    messages.value = []
  }
}

async function switchSession(id) {
  if (id === currentSessionId.value) return
  cancelPending()
  currentSessionId.value = id
  persistSession()
  await loadHistory()
}

// 取消进行中的请求（切会话/新建时防止旧回复污染当前界面）
const pendingCtrl = ref(null)
function cancelPending() {
  if (pendingCtrl.value) {
    pendingCtrl.value.abort()
    pendingCtrl.value = null
  }
}

async function newSession() {
  cancelPending()
  try {
    const res = await createSession({ title: '' })
    currentSessionId.value = res.data.sessionId
    persistSession()
    messages.value = []
    await refreshSessions()
    scrollToBottom()
  } catch (e) {
    ElMessage.error('新建会话失败：' + (e.message || e))
  }
}

async function removeSession(s) {
  try {
    await ElMessageBox.confirm(`删除会话「${s.title}」？该会话的对话记录将无法恢复。`, '删除会话', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch (e) { return }
  try {
    if (s.sessionId === currentSessionId.value) cancelPending()
    await deleteSession(s.sessionId)
    if (s.sessionId === currentSessionId.value) currentSessionId.value = ''
    await refreshSessions()
    ElMessage.success('会话已删除')
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || e))
  }
}

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
function renderMarkdown(s) {
  let html = escapeHtml(s || '')
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/^### (.*)$/gm, '<div class="md-h3">$1</div>')
  html = html.replace(/^## (.*)$/gm, '<div class="md-h2">$1</div>')
  html = html.replace(/^[-*] (.*)$/gm, '<div class="md-li">• $1</div>')
  html = html.replace(/\n/g, '<br/>')
  return html
}

function fill(s) { question.value = s }

async function send() {
  const q = question.value.trim()
  if (!q || loading.value) return
  // 没有当前会话先自动建一个
  if (!currentSessionId.value) {
    const res = await createSession({ title: '' })
    currentSessionId.value = res.data.sessionId
    persistSession()
  }
  cancelPending()
  const ctrl = new AbortController()
  pendingCtrl.value = ctrl
  const reqSessionId = currentSessionId.value

  messages.value.push({ role: 'user', content: q })
  question.value = ''
  loading.value = true
  messages.value.push({ role: 'ai', content: '思考中…' })
  scrollToBottom()
  try {
    const res = await chatAsk(reqSessionId, q, ctrl.signal)
    // 只有当前仍在原会话才更新界面；否则答案已由后端保存，切回该会话即可看到
    if (reqSessionId === currentSessionId.value) {
      messages.value[messages.value.length - 1].content = res.data
      await refreshSessions() // 更新标题/活跃时间
    }
  } catch (e) {
    // 主动取消（切换/新建）不提示；真正的失败才提示
    if (e.name !== 'CanceledError' && reqSessionId === currentSessionId.value) {
      messages.value[messages.value.length - 1].content = '请求失败：' + (e.message || e) + '（请确认后端已启动）'
    }
  } finally {
    if (reqSessionId === currentSessionId.value) {
      loading.value = false
      pendingCtrl.value = null
      scrollToBottom()
    }
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight
  })
}

onMounted(async () => {
  await refreshSessions()
  await loadHistory()
})
</script>

<style scoped>
.chat-card { height: 100%; display: flex; flex-direction: column; border-radius: 12px; }
.chat-card :deep(.el-card__body) { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.chat-layout { flex: 1; display: flex; min-height: 0; }

/* 左侧会话栏 */
.session-side {
  width: 200px; flex-shrink: 0; margin-right: 12px;
  display: flex; flex-direction: column;
  background: #f7f8fa; border: 1px solid #e5e7eb; border-radius: 10px;
  padding: 10px; min-height: 0;
}
.new-btn { width: 100%; margin-bottom: 10px; }
.session-list { flex: 1; overflow-y: auto; min-height: 0; }
.session-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 10px; border-radius: 8px; cursor: pointer;
  font-size: 13px; color: #374151; margin-bottom: 2px;
}
.session-item:hover { background: #eef0f3; }
.session-item.active { background: #0f766e; color: #fff; }
.session-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.del-icon { visibility: hidden; cursor: pointer; flex-shrink: 0; font-size: 14px; }
.session-item:hover .del-icon { visibility: visible; }
.session-item.active .del-icon:hover { color: #fca5a5; }
.session-empty { color: #9ca3af; font-size: 13px; text-align: center; padding: 20px 0; }

/* 右侧聊天区 */
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.chat-box {
  flex: 1; min-height: 0; overflow-y: auto; padding: 10px 4px;
  background: #fafbfc; border: 1px solid #e5e7eb; border-radius: 10px;
  margin-bottom: 12px;
}
.msg { margin-bottom: 12px; display: flex; align-items: flex-start; }
.msg.user { flex-direction: row-reverse; }
.avatar {
  width: 32px; height: 32px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 13px; background: #0f766e;
}
.msg.user .avatar { background: #2563eb; }
.bubble {
  max-width: 80%; padding: 10px 14px; border-radius: 12px;
  background: #fff; border: 1px solid #e5e7eb; font-size: 14px; line-height: 1.7;
  word-break: break-word;
}
.msg.user .bubble { background: #2563eb; color: #fff; border-color: #2563eb; }
.md-h2 { font-size: 15px; font-weight: 700; margin: 6px 0 2px; }
.md-h3 { font-size: 14px; font-weight: 700; margin: 4px 0 2px; }
.md-li { margin: 2px 0; }
.input-row { display: flex; gap: 8px; }
.suggest { margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap; }
.suggest-tag { cursor: pointer; }
</style>
