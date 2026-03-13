# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Full architecture details: `memory/architecture.md`

## Project: FristAgent
智能 PR/MR 代码审查 Web 应用。监听 GitHub/GitLab 的 PR/MR 事件，自动扫描 Diff，通过 Skill + LLM Agent 给出代码合规性、风险、BUG 和优化建议，结果可视化并通知相关人员。

## Tech Stack
- **Backend**: Java 21, Spring Boot 3, Maven
- **Frontend**: Vue 3 + Vite + Element Plus
- **Storage**: MySQL + Redis
- **LLM**: OpenAI-compatible protocol（endpoint/key/model 均可配置）

## Common Commands

```bash
# 后端构建
mvn clean install

# 后端跳过测试构建
mvn clean install -DskipTests

# 运行单个测试
mvn test -Dtest=ClassName#methodName

# 前端开发
cd fristagent-web && npm install && npm run dev

# 前端构建
cd fristagent-web && npm run build
```

## Module Structure
```
fristagent/
├── fristagent-web/        # Vue 3 前端
├── fristagent-server/     # Spring Boot 主服务
│   ├── webhook/           # GitHub + GitLab Webhook 接入（统一 MergeRequestEvent 模型）
│   ├── diff/              # Diff 解析与结构化
│   ├── skill/             # Skill 引擎（加载/热切换/管理）
│   ├── agent/             # Agent 核心（编排 Skill + LLM，聊天口工具调用）
│   ├── llm/               # LLM Gateway（统一 OpenAI 协议客户端）
│   ├── context/           # Redis 对话上下文管理
│   ├── scan/              # 扫描任务管理 + 结果持久化
│   ├── notify/            # 飞书个人消息 + 邮件
│   ├── chat/              # 聊天口 WebSocket API
│   └── config/            # 各项配置管理 API
└── fristagent-skill/      # 内置 Skill 资源文件
```

## Skill System（核心设计）
- Skill 是 Claude Code skill 格式的 Markdown 文件，从 GitHub 下载管理
- 每次扫描只使用**一个** active skill（存储于 Redis `active_skill` key）
- 热切换：更新 Redis + WebSocket 广播，无需重启
- 内置 3 个 Skill（首次启动自动注册）：UniversalCodeReviewer、LangChain-CR-Pro、TeamStyleEnforcer
- **切换入口有两处**：`/config/skills` 页面 和 `/chat` 聊天口（自然语言指令）

## Key Architectural Decisions
- **GitHub + GitLab 都支持**，通过 `WebhookAdapter` 接口抽象，统一转换为 `MergeRequestEvent`
- **LLM Gateway** 统一封装 OpenAI 协议调用，所有模型走同一客户端，切换只需改配置
- **飞书通知发给个人**（非群消息），通过飞书开放平台机器人 + `open_id` 定向发送
- **无登录**，内网使用
- **WebSocket** 用于扫描进度实时推送 + Skill 切换状态同步

## Dev Phases
1. **Phase 1**（优先）: Webhook 接入 → Diff 解析 → Skill 调用 → LLM 扫描 → 结果存储 → 飞书/邮件通知
2. **Phase 2**: WebSocket 实时进度 → 扫描详情页 → 历史查询
3. **Phase 3**: 聊天口 + RAG → Skill 热安装 → 评分趋势分析

