#!/bin/bash
# ============================================
# 乒乓龙 · 一键启动（双击本文件即可运行）
# 功能：启动 Docker 依赖 → 后端(8080) → 前端(5173) → 打开浏览器
# ============================================
cd "$(dirname "$0")" || exit 1
mkdir -p logs

echo "=============================================="
echo "  乒乓龙 · 一键启动"
echo "=============================================="

# 1. Docker 依赖
echo "[1/4] 检查 Docker 依赖 (MySQL/Redis/ES)..."
if ! docker info >/dev/null 2>&1; then
  echo "❌ Docker 未运行，请先启动 Docker Desktop 或 OrbStack 后重试"
  read -r -p "按回车退出..." _
  exit 1
fi
docker compose up -d >/dev/null 2>&1
for _ in $(seq 1 60); do
  curl -sf http://127.0.0.1:9200 >/dev/null 2>&1 && break
  sleep 5
done
if curl -sf http://127.0.0.1:9200 >/dev/null 2>&1; then
  echo "   ✅ MySQL/Redis/ES 就绪"
else
  echo "   ⚠️ ES 未就绪（后端会自动重试，可稍后检查 docker compose ps）"
fi

# 2. 后端
echo "[2/4] 启动后端 (8080)..."
if lsof -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "   ✅ 后端已在运行"
else
  nohup mvn -q spring-boot:run > logs/backend.log 2>&1 &
  for _ in $(seq 1 80); do
    curl -sf http://127.0.0.1:8080/api/player/list >/dev/null 2>&1 && break
    sleep 3
  done
  if curl -sf http://127.0.0.1:8080/api/player/list >/dev/null 2>&1; then
    echo "   ✅ 后端就绪"
  else
    echo "   ⚠️ 后端仍在启动，日志：logs/backend.log（打开页面后稍等刷新即可）"
  fi
fi

# 3. 前端
echo "[3/4] 启动前端 (5173)..."
if [ ! -d frontend/node_modules ]; then
  echo "   首次运行，安装前端依赖..."
  (cd frontend && npm install --registry=https://registry.npmmirror.com)
fi
if lsof -iTCP:5173 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "   ✅ 前端已在运行"
else
  (cd frontend && nohup npm run dev > ../logs/frontend.log 2>&1 &)
  for _ in $(seq 1 30); do
    curl -sf http://127.0.0.1:5173 >/dev/null 2>&1 && break
    sleep 2
  done
  if curl -sf http://127.0.0.1:5173 >/dev/null 2>&1; then
    echo "   ✅ 前端就绪"
  else
    echo "   ⚠️ 前端启动较慢，日志：logs/frontend.log"
  fi
fi

# 4. 打开浏览器
echo "[4/4] 打开浏览器..."
open http://127.0.0.1:5173

echo "=============================================="
echo "  已就绪！前端 http://localhost:5173  后端 http://localhost:8080"
echo "  停止服务请双击 stop.command"
echo "=============================================="
