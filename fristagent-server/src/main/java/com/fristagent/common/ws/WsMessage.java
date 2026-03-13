package com.fristagent.common.ws;

import lombok.Builder;
import lombok.Data;

/**
 * WebSocket 推送消息统一结构
 *
 * type 枚举：
 *   SCAN_PROGRESS  — 扫描进度更新（step + percent）
 *   SCAN_DONE      — 扫描完成（含 score/summary）
 *   SCAN_FAILED    — 扫描失败
 *   SKILL_SWITCHED — Skill 热切换
 */
@Data
@Builder
public class WsMessage {

    private String type;

    // SCAN_* 事件
    private Long   taskId;
    private String status;
    private String step;       // 当前步骤描述，如 "正在分析 Diff..."
    private Integer percent;   // 0-100 进度百分比
    private Integer score;
    private String summary;

    // SKILL_SWITCHED 事件
    private String skillName;
}
