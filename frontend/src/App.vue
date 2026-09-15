<template>
  <el-container class="app">
    <el-header class="header">
      <div class="header-left">
        <el-icon :size="24" color="#fff"><Cpu /></el-icon>
        <span class="title">乒乓龙 · 乒乓球智能体助手</span>
      </div>
      <el-tag effect="dark" type="success" round>RAG + Agent · Hybrid 检索</el-tag>
    </el-header>

    <el-main class="main">
      <div class="layout">
        <!-- 左：聊天（自适应高度填满） -->
        <div class="chat-col">
          <ChatPanel />
        </div>

        <!-- 右：Tabs 收纳三个面板，整页无需滚动 -->
        <div class="right-col">
          <el-card shadow="never" class="side-card">
            <el-tabs v-model="activeTab" class="side-tabs">
              <el-tab-pane label="选手档案" name="player">
                <PlayerPanel />
              </el-tab-pane>
              <el-tab-pane label="比赛记录" name="match">
                <MatchPanel />
              </el-tab-pane>
              <el-tab-pane label="知识库" name="doc">
                <DocPanel />
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </div>
      </div>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import ChatPanel from './components/ChatPanel.vue'
import PlayerPanel from './components/PlayerPanel.vue'
import MatchPanel from './components/MatchPanel.vue'
import DocPanel from './components/DocPanel.vue'

const activeTab = ref('player')
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; overflow: hidden; }
body {
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: linear-gradient(135deg, #f0f4f8 0%, #e6edf5 100%);
}
.app { height: 100vh; display: flex; flex-direction: column; }
.header {
  background: linear-gradient(90deg, #0f766e 0%, #115e59 100%);
  display: flex; align-items: center; justify-content: space-between;
  box-shadow: 0 2px 12px rgba(0,0,0,.15);
  flex-shrink: 0;
}
.header-left { display: flex; align-items: center; gap: 10px; }
.header .title { color: #fff; font-size: 20px; font-weight: 700; }
.main { flex: 1; padding: 16px; overflow: hidden; }
.layout { display: flex; gap: 16px; height: 100%; }
.chat-col { flex: 3; min-width: 0; height: 100%; }
.right-col { flex: 2; min-width: 0; height: 100%; }
.side-card { height: 100%; border-radius: 12px; display: flex; flex-direction: column; }
.side-card :deep(.el-card__body) { flex: 1; display: flex; flex-direction: column; overflow: hidden; padding-top: 4px; }
.side-tabs { flex: 1; display: flex; flex-direction: column; }
.side-tabs :deep(.el-tabs__content) { flex: 1; overflow-y: auto; padding-right: 4px; }
.side-tabs :deep(.el-tabs__header) { margin-bottom: 8px; }
.card-title { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; color: #115e59; }
.card-title .el-icon { color: #0f766e; }
</style>
