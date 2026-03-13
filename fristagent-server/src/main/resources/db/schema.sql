CREATE TABLE IF NOT EXISTS `repo_config` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`           VARCHAR(128) NOT NULL COMMENT '仓库名称',
    `platform`       VARCHAR(16)  NOT NULL COMMENT 'GITHUB / GITLAB',
    `repo_url`       VARCHAR(512) NOT NULL COMMENT '仓库地址',
    `webhook_secret` VARCHAR(256) COMMENT 'Webhook 校验密钥',
    `enabled`        TINYINT(1)   NOT NULL DEFAULT 1,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `admin_user` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`            VARCHAR(64)  NOT NULL,
    `email`           VARCHAR(256) NOT NULL,
    `feishu_open_id`  VARCHAR(128) COMMENT '飞书 open_id，用于个人消息推送',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 仓库与管理员多对多
CREATE TABLE IF NOT EXISTS `repo_admin` (
    `repo_id`  BIGINT NOT NULL,
    `admin_id` BIGINT NOT NULL,
    PRIMARY KEY (`repo_id`, `admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `skill_registry` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`         VARCHAR(128) NOT NULL UNIQUE COMMENT 'Skill 唯一标识',
    `display_name` VARCHAR(256) NOT NULL,
    `version`      VARCHAR(32),
    `description`  TEXT,
    `source_url`   VARCHAR(512) COMMENT 'GitHub 地址（自定义 Skill）',
    `skill_type`   VARCHAR(16)  NOT NULL DEFAULT 'BUILTIN' COMMENT 'BUILTIN / CUSTOM',
    `is_active`    TINYINT(1)   NOT NULL DEFAULT 0,
    `installed_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `scan_task` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repo_id`      BIGINT       NOT NULL,
    `platform`     VARCHAR(16)  NOT NULL,
    `pr_number`    VARCHAR(32)  NOT NULL,
    `pr_title`     VARCHAR(512),
    `pr_author`    VARCHAR(128),
    `pr_url`       VARCHAR(512),
    `source_ref`   VARCHAR(256),
    `target_branch`VARCHAR(256),
    `skill_name`   VARCHAR(128) COMMENT '本次使用的 Skill',
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SCANNING/DONE/FAILED',
    `score`        INT COMMENT '综合评分 0-100',
    `summary`      TEXT COMMENT 'Agent 综合总结',
    `started_at`   DATETIME,
    `finished_at`  DATETIME,
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `scan_issue` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `task_id`     BIGINT       NOT NULL,
    `file_path`   VARCHAR(512),
    `line_start`  INT,
    `line_end`    INT,
    `issue_type`  VARCHAR(32)  COMMENT 'BUG / SECURITY / STYLE / PERFORMANCE / SUGGESTION',
    `severity`    VARCHAR(16)  COMMENT 'HIGH / MEDIUM / LOW',
    `description` TEXT         NOT NULL,
    `suggestion`  TEXT,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `llm_config` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `endpoint`        VARCHAR(512) NOT NULL,
    `api_key`         VARCHAR(512) NOT NULL,
    `model`           VARCHAR(128) NOT NULL,
    `timeout_seconds` INT          NOT NULL DEFAULT 120,
    `max_tokens`      INT          NOT NULL DEFAULT 4096,
    `is_active`       TINYINT(1)   NOT NULL DEFAULT 1,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `notify_config` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `feishu_app_id`  VARCHAR(128),
    `feishu_app_secret` VARCHAR(256),
    `mail_host`      VARCHAR(256),
    `mail_port`      INT,
    `mail_username`  VARCHAR(256),
    `mail_password`  VARCHAR(256),
    `mail_from`      VARCHAR(256),
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `session_key` VARCHAR(64) NOT NULL UNIQUE,
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
    `session_id` BIGINT       NOT NULL,
    `role`       VARCHAR(16)  NOT NULL COMMENT 'user / assistant',
    `content`    TEXT         NOT NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化内置 Skill
INSERT IGNORE INTO `skill_registry` (`name`, `display_name`, `version`, `description`, `skill_type`, `is_active`) VALUES
('universal-code-reviewer', 'UniversalCodeReviewer', '1.0.0',
 '万能代码审查官，支持 Python/Java/Go/JS/TS/C#/Rust，内置 OWASP Top 10 安全检查、规范检查、性能反模式识别',
 'BUILTIN', 1),
('langchain-cr-pro', 'LangChain-CR-Pro', '1.0.0',
 '专为 LLM 应用优化的审查器，能识别 prompt injection、token 泄露、异步阻塞等风险',
 'BUILTIN', 0),
('team-style-enforcer', 'TeamStyleEnforcer', '1.0.0',
 '团队风格守护者，支持上传 .style.yaml 动态适配审查规则，全语言支持',
 'BUILTIN', 0);
