<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-title">
        <el-icon><ChatDotRound /></el-icon>
        <span>对话问答（Agent 自动选择工具）</span>
      </div>
    </template>

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
        placeholder="试试问：张三的打法 / 我和李四的开球网比赛 / 长胶怎么应对"
        clearable
        @keyup.enter="send"
      />
      <el-button type="primary" :loading="loading" @click="send">发送</el-button>
    </div>
    <div class="suggest">
      <el-tag v-for="s in suggests" :key="s" size="small" class="suggest-tag" @click="fill(s)">{{ s }}</el-tag>
    </div>
  </el-card>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { chatAsk } from '../api'

const messages = ref([])
const question = ref('')
const loading = ref(false)
const chatBox = ref(null)
const suggests = ['张三的打法是什么', '我和李四的比赛记录', '长胶怎么应对']

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
function renderMarkdown(s) {
  // 简易渲染：标题/粗体/列表/换行 → HTML
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
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  loading.value = true
  messages.value.push({ role: 'ai', content: '思考中…' })
  scrollToBottom()
  try {
    const res = await chatAsk(q)
    messages.value[messages.value.length - 1].content = res.data
  } catch (e) {
    messages.value[messages.value.length - 1].content = '请求失败：' + (e.message || e) + '（请确认后端已启动）'
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight
  })
}
</script>

<style scoped>
.chat-box {
  height: 420px; overflow-y: auto; padding: 10px 4px;
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
