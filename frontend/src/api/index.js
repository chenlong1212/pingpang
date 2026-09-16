import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 120000
})

// 聊天（JSON 携带 sessionId 实现多轮会话记忆，返回纯文本）
export const chatAsk = (sessionId, question) =>
  api.post('/chat/ask', { sessionId, question }, {
    headers: { 'Content-Type': 'application/json' },
    responseType: 'text'
  })
// 加载会话历史（刷新后恢复聊天界面）
export const chatHistory = (sessionId) => api.get('/chat/history', { params: { sessionId } })
// 会话管理：列表 / 新建 / 删除
export const chatSessions = () => api.get('/chat/sessions')
export const createSession = (data) => api.post('/chat/sessions', data)
export const deleteSession = (sessionId) => api.delete(`/chat/sessions/${sessionId}`)

// 选手
export const savePlayer = (data) => api.post('/player/save', data)
export const updatePlayer = (id, data) => api.put(`/player/${id}`, data)
export const deletePlayer = (id) => api.delete(`/player/${id}`)
export const listPlayers = () => api.get('/player/list')

// 比赛
export const saveMatch = (data) => api.post('/match/save', data)
export const updateMatch = (id, data) => api.put(`/match/${id}`, data)
export const deleteMatch = (id) => api.delete(`/match/${id}`)
export const listMatches = () => api.get('/match/list')
export const pageMatches = (page, size, keyword) => api.get('/match/page', { params: { page, size, keyword } })

// 知识库文档
export const uploadDoc = (file, sync) => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('sync', sync)
  return api.post('/doc/upload', fd)
}
export const uploadText = (data, sync) => api.post('/doc/uploadText', { ...data, sync })
export const listDocs = () => api.get('/doc/list')
export const getDocDetail = (docId) => api.get('/doc/detail', { params: { docId } })
export const deleteDoc = (docId) => api.delete(`/doc/${docId}`)
