# FristAgent Architecture

## Tech Stack
- Backend: Java 21, Spring Boot 3, Maven
- Frontend: Vue 3 + Vite + Element Plus
- DB: MySQL + Redis
- LLM: OpenAI-compatible protocol (configurable endpoint/key/model)

## Module Structure
```
fristagent/
├── fristagent-web/          # Vue 3 frontend
├── fristagent-server/       # Spring Boot main service
│   ├── webhook/             # GitHub + GitLab Webhook adapters
│   ├── diff/                # Diff parsing & structuring
│   ├── skill/               # Skill engine (load/switch/manage)
│   ├── agent/               # Agent core (orchestrate Skill + LLM)
│   ├── llm/                 # LLM Gateway (OpenAI protocol)
│   ├── context/             # Redis conversation context
│   ├── scan/                # Scan task management + result persistence
│   ├── notify/              # Feishu personal message + email
│   ├── chat/                # Chat API (WebSocket)
│   └── config/              # Configuration management API
└── fristagent-skill/        # Built-in Skill resource files
```

## Skill System
- Skills are Claude Code skill files (Markdown format) downloaded from GitHub
- 3 built-in skills packaged in resources, auto-registered on startup:
  - UniversalCodeReviewer
  - LangChain-CR-Pro
  - TeamStyleEnforcer
- Only ONE skill active at a time (stored in Redis as `active_skill`)
- Hot-swap: change active skill instantly, no restart needed
- Custom skills: user provides GitHub URL → backend downloads → stored in ${data.dir}/skills/{name}/

### Skill Switch - Two Frontend Entrypoints
1. `/config/skills` page: radio-style activation buttons
2. `/chat` page: natural language ("切换到 LangChain-CR-Pro") → Agent calls switchSkill() tool

### Skill State Sync
Both entrypoints operate same Redis `active_skill` key.
WebSocket broadcasts `SkillSwitchedEvent` to keep both pages in sync.

### Skill API
- GET    /api/skills                    # list all installed
- POST   /api/skills/install            # install from GitHub URL
- DELETE /api/skills/{name}             # uninstall
- PUT    /api/skills/{name}/activate    # activate (hot-swap)
- GET    /api/skills/active             # get current active

## LLM Gateway
- Unified OpenAI-compatible client
- Config: endpoint URL + API key + model name (all configurable via UI)
- Supports: OpenAI / Azure OpenAI / Ollama / vLLM / DeepSeek / Qwen / any OpenAI-compatible

## Webhook (GitHub + GitLab both supported)
Unified event model:
```java
record MergeRequestEvent(Platform platform, String repoUrl, String prNumber,
    String title, String author, String diffUrl, String targetBranch)
```
Each platform has its own WebhookAdapter implementation.

## Notifications
- Feishu: direct personal messages via Feishu Open Platform bot (using open_id)
- Email: SMTP
- Admin config: name + email + feishu_open_id per repo

## Frontend Pages
| Route | Page |
|-------|------|
| / | Dashboard (stats, score trends, recent activity) |
| /scans | PR scan list (status, score, realtime progress) |
| /scans/:id | Scan detail (diff view + issue annotations + Agent suggestions) |
| /chat | Chat interface (query history, switch skills) |
| /config/repos | Repo config (Git URL, Webhook, admin binding) |
| /config/skills | Skill management (built-in list, activate/switch, install 3rd party) |
| /config/model | LLM config (endpoint/key/model) |
| /config/notify | Notification config (Feishu app, SMTP) |

## Core DB Tables
- repo_config: platform, repo_url, webhook_secret, admins
- admin_user: name, email, feishu_open_id, repo_ids
- scan_task: repo_id, pr_number, title, author, status, skill_name, score, timestamps
- scan_issue: task_id, file_path, line_start, line_end, issue_type, severity, description, suggestion
- skill_registry: name, version, source, type(BUILTIN/CUSTOM), is_active, installed_at
- chat_message: session_id, role, content, created_at

## Agent Design
PR Diff → Skill Pipeline (active skill only) → LLM reasoning → structured report
Agent capabilities in chat: switch_skill(), list_skills(), query_scan_history(), summarize_issues()

## Dev Phases
- Phase 1: Webhook → Diff parsing → Skill → LLM scan → Result storage → Feishu notify
- Phase 2: WebSocket realtime progress → Scan detail page → History query
- Phase 3: Chat + RAG → Skill hot-install → Score trend analysis