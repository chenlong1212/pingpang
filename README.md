# 乒乓龙 · 乒乓球智能体助手

基于 RAG + Agent 的乒乓球私有知识问答平台，支持选手资料查询、比赛记录分析、领域知识问答。

## 技术栈

- Java 21 + Spring Boot 3.5.7 + Spring AI 1.1.2（GA）
- MySQL 8（选手档案 / 比赛记录 / 文档元数据，结构化存储）
- Elasticsearch 8（IK 分词 + Dense Vector 双路召回，RRF 融合；由 Spring Boot 自动配置）
- Redis 7（选手/比赛查询缓存，@Cacheable 30 分钟 TTL）
- Kafka 3.7（两阶段异步文档流水线：解析→切分/向量化→索引，重试 + 死信队列）
- 对话模型：agnes-2.5-flash（OpenAI 兼容）
- Embedding：硅基流动 BAAI/bge-m3（1024 维，中文效果好）
- Reranker：硅基流动 BAAI/bge-reranker-v2-m3（重排优化排序）

## 目录结构

```
pingpang/
├── start.command            # ⭐ 一键启动：双击运行（Docker→后端→前端→开浏览器）
├── stop.command             # 一键停止前后端
├── pom.xml
├── docker-compose.yml       # MySQL + Redis + ES（含IK分词）一键启动
├── Dockerfile.es            # ES 镜像 + IK 分词插件
├── frontend/                # Vue3 前端（Vite + Element Plus + axios）
│   ├── package.json
│   ├── vite.config.js       # /api 代理到 8080
│   └── src/
│       ├── App.vue
│       ├── api/index.js     # axios 接口封装
│       └── components/      # ChatPanel / PlayerPanel / MatchPanel / DocPanel
└── src/main/
    ├── java/com/tabletennis/rag/
    │   ├── PingpongRagApplication.java
    │   ├── config/           # SpringAI / CORS / Jackson 配置（ES 走 Spring Boot 自动配置）
    │   ├── entity/           # TtPlayer / TtMatchRecord / TtKnowledgeDoc
    │   ├── repository/       # JPA Repository
    │   ├── service/          # 选手、比赛业务
    │   ├── rag/              # HybridRAGService（BM25+向量kNN+RRF+Reranker）
    │   ├── ai/               # TableTennisTools（Agent工具）/ QueryRewriteService
    │   ├── controller/       # 聊天 / 选手 / 比赛 / 文档上传接口
    │   └── init/             # 启动时自动创建 ES 索引
    └── resources/
        ├── application.yml
        └── static/index.html # 旧版原生前端（保留参考，实际使用 Vue3 前端）
```

## 启动步骤（推荐）

### 方式一：双击一键启动 ⭐

1. 确保已安装 Docker、Java 21、Maven、Node.js 18+
2. 双击 `start.command`（首次如被系统拦截：右键 → 打开）
3. 脚本自动：启动 Docker 依赖 → 启动后端(8080) → 启动前端(5173) → 打开浏览器
4. 停止服务双击 `stop.command`（Docker 依赖保留，可 `docker compose down` 一并停止）

### 方式二：手动启动

```bash
# 1. 依赖
docker compose up -d

# 2. 后端（8080）
mvn spring-boot:run

# 3. 前端（5173，另开终端）
cd frontend && npm install && npm run dev

# 4. 浏览器
open http://localhost:5173
```

- 录入选手档案（姓名 / 左右手 / 直横拍 / 打法）
- 录入比赛记录（对手 / 日期 / 类型 / 比分）
- 上传 txt / md / pdf / docx 知识库文档，或手动录入文本，自动切片向量化入库
- 聊天框提问，Agent 自动选择工具：
  - 「张三的打法是什么」→ 选手档案工具
  - 「我和李四的开球网比赛」→ 比赛记录工具
  - 「长胶怎么应对」「反胶和正胶的区别」→ 知识库 RAG 检索

## 核心接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/chat/ask | 聊天问答（text/plain 传问题） |
| POST | /api/player/save | 保存选手档案 |
| GET | /api/player/list | 选手列表 |
| POST | /api/match/save | 保存比赛记录 |
| GET | /api/match/list | 比赛列表 |
| POST | /api/doc/upload | 上传知识库文档（multipart，支持 txt/md/pdf/docx，Tika 解析） |
| POST | /api/doc/uploadText | 手动文本录入（JSON：title + content） |
| GET | /api/doc/list | 文档元数据列表 |

> 前端通过 Vite 代理访问 `/api`（开发模式 5173 → 8080），生产构建时可让后端托管 `frontend/dist`。

## RAG 链路说明

- **双路召回**：ES IK 分词 BM25 稀疏检索 + dense_vector kNN 余弦向量检索
- **RRF 融合**：两路 top-K 排名按 `1/(K+rank)` 加权融合（K=60）
- **Reranker**：bge-reranker-v2-m3 对 RRF 候选按相关性打分重排（失败自动降级原序）
- **Agentic 路由**：选手/比赛/知识库三个 @Tool，LLM 自主选择调用，不硬编码路由
- **Query Rewrite**：口语化问题先经 LLM 改写为标准 Query 并识别意图（player/match/knowledge）
- **Kafka 异步流水线**：文件上传/文本录入 → `doc-upload` topic → 消费端解析→切片→向量化→ES 索引；失败重试 3 次（指数退避），仍失败进入 `doc-upload-dlq` 死信队列并标记文档失败

## 说明

- Kafka 异步文档流水线（解析→切分/向量化→索引，重试 + 死信队列）已实现：上传接口先落盘并提交消息，消费端异步完成解析/切片/向量化/索引，接口即时返回，任务可恢复。
- API Key 已配置在 `application.yml`（不入库），模板见 `application.example.yml`，如更换请同步修改。
