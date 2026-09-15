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
      <el-row :gutter="16">
        <!-- 左栏：聊天 -->
        <el-col :xs="24" :md="14">
          <ChatPanel />
        </el-col>

        <!-- 右栏：数据管理 -->
        <el-col :xs="24" :md="10">
          <PlayerPanel />
          <MatchPanel />
          <DocPanel />
        </el-col>
      </el-row>
    </el-main>
  </el-container>
</template>

<script setup>
import ChatPanel from './components/ChatPanel.vue'
import PlayerPanel from './components/PlayerPanel.vue'
import MatchPanel from './components/MatchPanel.vue'
import DocPanel from './components/DocPanel.vue'
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; }
body {
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: linear-gradient(135deg, #f0f4f8 0%, #e6edf5 100%);
}
.app { min-height: 100vh; }
.header {
  background: linear-gradient(90deg, #0f766e 0%, #115e59 100%);
  display: flex; align-items: center; justify-content: space-between;
  box-shadow: 0 2px 12px rgba(0,0,0,.15);
}
.header-left { display: flex; align-items: center; gap: 10px; }
.header .title { color: #fff; font-size: 20px; font-weight: 700; }
.main { padding: 20px; }
.el-card { border-radius: 12px; margin-bottom: 16px; }
.card-title { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; color: #115e59; }
.card-title .el-icon { color: #0f766e; }
</style>
