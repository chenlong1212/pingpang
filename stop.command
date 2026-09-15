#!/bin/bash
# ============================================
# 乒乓龙 · 一键停止（双击本文件即可运行）
# 功能：停止前端(5173) 与后端(8080)，Docker 依赖保留
# ============================================
cd "$(dirname "$0")" || exit 1

echo "停止前端 (5173)..."
if lsof -tiTCP:5173 -sTCP:LISTEN >/dev/null 2>&1; then
  lsof -tiTCP:5173 -sTCP:LISTEN | xargs kill 2>/dev/null
  echo "   ✅ 前端已停止"
else
  echo "   前端未在运行"
fi

echo "停止后端 (8080)..."
if lsof -tiTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  lsof -tiTCP:8080 -sTCP:LISTEN | xargs kill 2>/dev/null
  sleep 1
  echo "   ✅ 后端已停止"
else
  echo "   后端未在运行"
fi

echo ""
echo "完成。Docker 依赖 (MySQL/Redis/ES) 保留运行，如需一并停止：docker compose down"
