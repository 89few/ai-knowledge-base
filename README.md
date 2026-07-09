# AI Knowledge Base

一个基于 Spring Boot 的开源 RAG（检索增强生成）知识库应用。它支持上传文档、文本切片、向量检索、连续对话、流式回答、联网搜索以及图片 OCR，并提供可直接使用的 Web 界面。

## 功能

- 用户注册、登录、退出与 Token 鉴权
- 创建和管理知识库
- 上传 PDF、DOCX、TXT 与图片等文件
- PDFBox、Apache POI、Tesseract OCR 文本提取
- Ollama `nomic-embed-text` 本地向量化
- PostgreSQL + pgvector 语义检索
- OpenRouter 大模型问答
- NDJSON 流式输出与多轮会话历史
- Tavily 联网搜索（可选）
- Redis 请求限流
- 单页 Web UI，无需额外前端构建

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5、Spring MVC |
| 数据访问 | Spring Data JPA、Hibernate |
| 数据库 | PostgreSQL 16、pgvector |
| 缓存/限流 | Redis 7 |
| 大模型 | OpenRouter（默认 `z-ai/glm-4.5-air`） |
| Embedding | Ollama、`nomic-embed-text` |
| 文档解析 | PDFBox、Apache POI、Tess4J |
| 前端 | 原生 HTML、CSS、JavaScript |
| 构建 | Maven Wrapper |

## 工作流程

```text
文档上传
  → 文本提取 / OCR
  → 文本切片
  → Ollama 生成向量
  → PostgreSQL/pgvector 保存

用户提问
  → 向量召回相关片段
  → 组合知识片段与对话历史
  → OpenRouter 生成回答
  → NDJSON 流式返回前端
```

## 环境要求

- JDK 17 或更高版本
- Docker Desktop（推荐用于 PostgreSQL、Redis 和 Ollama）
- OpenRouter API Key
- Tesseract OCR（仅图片 OCR 需要）
- Tavily API Key（仅联网搜索需要）

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/89few/ai-knowledge-base.git
cd ai-knowledge-base
```

### 2. 启动基础服务

```bash
docker compose up -d
docker exec ai-kb-ollama ollama pull nomic-embed-text
```

Compose 会启动：

- PostgreSQL/pgvector：`localhost:5432`
- Redis：`localhost:6379`
- Ollama：`localhost:11434`

### 3. 配置环境变量

复制 `.env.example`，并至少设置 `OPENROUTER_API_KEY`。注意：Spring Boot 不会自动读取 `.env` 文件；它主要作为配置清单使用，请把变量导入当前终端。

PowerShell：

```powershell
$env:OPENROUTER_API_KEY="你的 OpenRouter Key"
$env:TAVILY_API_KEY="你的 Tavily Key" # 可选
```

Bash：

```bash
export OPENROUTER_API_KEY="你的 OpenRouter Key"
export TAVILY_API_KEY="你的 Tavily Key" # 可选
```

### 4. 启动应用

Windows：

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux：

```bash
./mvnw spring-boot:run
```

打开 <http://localhost:8080>。

## VS Code 运行

建议安装：

- Extension Pack for Java
- Spring Boot Extension Pack
- Lombok Annotations Support for VS Code

用 VS Code 打开项目根目录，等待 Maven 导入完成，然后运行 `AiKnowledgeBaseApplication.java`，或直接使用上述 Maven 命令。

## 配置项

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OPENROUTER_API_KEY` | 无 | OpenRouter Key，必填 |
| `OPENROUTER_MODEL` | `z-ai/glm-4.5-air` | 大模型 slug |
| `TAVILY_API_KEY` | 空 | Tavily 联网搜索 Key |
| `DB_URL` | `jdbc:postgresql://localhost:5432/ai_kb` | 数据库地址 |
| `DB_USERNAME` | `postgres` | 数据库用户名 |
| `DB_PASSWORD` | `123456` | 数据库密码 |
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama 地址 |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | 向量模型 |
| `TESSERACT_DATA_PATH` | Windows 默认安装路径 | tessdata 路径 |
| `TESSERACT_LANGUAGE` | `chi_sim+eng` | OCR 语言 |

生产环境请务必修改数据库密码，不要使用默认值。

## 主要 API

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/auth/me` | 当前用户 |
| POST | `/api/kbs` | 创建知识库 |
| GET | `/api/kbs` | 知识库列表 |
| POST | `/api/documents/upload` | 上传知识库文档 |
| GET | `/api/documents` | 文档列表 |
| POST | `/api/vector/build` | 构建向量 |
| GET | `/api/vector/search` | 向量搜索 |
| GET | `/api/rag/ask` | 普通问答 |
| GET | `/api/rag/ask/stream` | NDJSON 流式问答 |
| GET | `/api/sessions` | 会话列表 |
| GET | `/api/web-search/search` | 联网搜索 |

需要登录的请求使用：

```http
Authorization: Bearer <token>
```

流式接口返回每行一个 JSON 对象，事件类型依次可能为 `sessionId`、`delta`、`sources`、`error` 和 `done`。

## 测试

确保 PostgreSQL 和 Redis 已运行：

```bash
./mvnw test
```

Windows：

```powershell
.\mvnw.cmd test
```

## 常见问题

### OpenRouter 返回模型 404

免费模型 slug 可能下线。设置一个当前可用的模型：

```powershell
$env:OPENROUTER_MODEL="z-ai/glm-4.5-air"
```

### 流式调用出现 `RST_STREAM: Protocol error`

项目的流式 OpenRouter 客户端已固定使用 HTTP/1.1，以规避部分上游节点重置 HTTP/2 SSE 流的问题。请确认使用的是最新代码并重启应用。

### 无法连接 PostgreSQL 或 Redis

```bash
docker compose ps
docker compose logs postgres
docker compose logs redis
```

### Ollama 没有 Embedding 模型

```bash
docker exec ai-kb-ollama ollama pull nomic-embed-text
```

### OCR 无法识别中文

确认 `TESSERACT_DATA_PATH` 下存在 `chi_sim.traineddata` 和 `eng.traineddata`。

## 安全说明

- 不要把 API Key、Token、数据库生产密码提交到 Git。
- `.env`、日志和本地 `apikey.txt` 已被 `.gitignore` 排除。
- Web UI 当前把登录 Token 保存在浏览器 `localStorage`，部署到公网前应启用 HTTPS，并评估改用安全 Cookie。
- 默认配置面向本地开发；公网部署前应增加 CORS、CSRF、访问控制、审计与反向代理限流。

## 项目结构

```text
src/main/java/org/aiknowledgebase
├── config       # Spring MVC 配置
├── controller   # REST API
├── dto          # 请求和响应对象
├── entity       # JPA 实体
├── repository   # 数据访问
└── service      # RAG、LLM、向量、文档、鉴权等服务

src/main/resources
├── application.yml
└── static/index.html
```

## 贡献

欢迎提交 Issue 和 Pull Request：

1. Fork 本仓库。
2. 创建功能分支：`git switch -c feature/your-feature`。
3. 提交修改并补充测试。
4. 推送分支并创建 Pull Request。

提交前请运行 `./mvnw test`，不要提交任何真实凭据或用户数据。

## License

本项目采用 [MIT License](LICENSE)。
