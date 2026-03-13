package com.fristagent.scan;

import com.fristagent.agent.AgentCore;
import com.fristagent.agent.model.ReviewResult;
import com.fristagent.common.ws.ScanWebSocketHandler;
import com.fristagent.common.ws.WsMessage;
import com.fristagent.diff.DiffParser;
import com.fristagent.diff.model.DiffContext;
import com.fristagent.notify.NotifyService;
import com.fristagent.scan.model.ScanIssue;
import com.fristagent.scan.model.ScanTask;
import com.fristagent.scan.repository.ScanIssueRepository;
import com.fristagent.scan.repository.ScanTaskRepository;
import com.fristagent.skill.engine.SkillEngine;
import com.fristagent.webhook.model.MergeRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫描服务：全流程编排入口
 * Webhook → DiffParser → AgentCore → 结果持久化 → 通知
 * 每个关键步骤通过 WebSocket 广播实时进度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanService {

    private final ScanTaskRepository scanTaskRepository;
    private final ScanIssueRepository scanIssueRepository;
    private final DiffParser diffParser;
    private final AgentCore agentCore;
    private final SkillEngine skillEngine;
    private final NotifyService notifyService;
    private final ScanWebSocketHandler wsHandler;

    @Async("taskExecutor")
    public void triggerScan(MergeRequestEvent event) {
        log.info("Scan triggered: platform={}, repo={}, pr={}",
                event.platform(), event.repoName(), event.prNumber());

        ScanTask task = createTask(event);

        try {
            // Step 1: 获取 Diff
            progress(task, "SCANNING", "正在连接仓库获取 Diff...", 10);
            DiffContext diff = diffParser.fetchAndParse(event);

            // Step 2: 准备 Skill
            progress(task, "SCANNING", "加载 Skill: " + task.getSkillName(), 30);

            // Step 3: LLM 分析（流式推送每个 chunk 到前端）
            progress(task, "SCANNING", "AI 分析中，请稍候...", 50);
            ReviewResult result = agentCore.review(diff, chunk ->
                    wsHandler.broadcast(WsMessage.builder()
                            .type("SCAN_LOG")
                            .taskId(task.getId())
                            .chunk(chunk)
                            .build()));

            // Step 4: 通知（先发，不影响最终状态）
            progress(task, "SCANNING", "发送通知...", 85);
            notifyService.notifyAdmins(task, result);

            // Step 5: 保存结果（最后写 DONE，确保不被后续 progress 覆盖）
            saveResult(task, result);

            // Done
            wsHandler.broadcast(WsMessage.builder()
                    .type("SCAN_DONE")
                    .taskId(task.getId())
                    .status("DONE")
                    .percent(100)
                    .score(result.score())
                    .summary(result.summary())
                    .build());

            log.info("Scan completed: taskId={}, score={}, issues={}",
                    task.getId(), result.score(), result.issues().size());

        } catch (Exception e) {
            log.error("Scan failed: taskId={}", task.getId(), e);
            task.setStatus("FAILED");
            task.setSummary("Scan failed: " + e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            scanTaskRepository.save(task);

            wsHandler.broadcast(WsMessage.builder()
                    .type("SCAN_FAILED")
                    .taskId(task.getId())
                    .status("FAILED")
                    .summary(e.getMessage())
                    .build());
        }
    }

    @Transactional
    protected ScanTask createTask(MergeRequestEvent event) {
        ScanTask task = new ScanTask();

        task.setRepoId(event.repoId());
        task.setPlatform(event.platform().name());
        task.setPrNumber(event.prNumber());
        task.setPrTitle(event.prTitle());
        task.setPrAuthor(event.prAuthor());
        task.setPrUrl(event.prUrl());
        task.setSourceRef(event.sourceRef());
        task.setTargetBranch(event.targetBranch());
        task.setSkillName(skillEngine.getActiveSkillName());
        task.setStatus("PENDING");
        task.setScore(null);
        task.setSummary(null);
        task.setStartedAt(LocalDateTime.now());
        task.setFinishedAt(null);
        return scanTaskRepository.save(task);
    }

    @Transactional
    protected void saveResult(ScanTask task, ReviewResult result) {
        task.setStatus("DONE");
        task.setScore(result.score());
        task.setSummary(result.summary());
        task.setFinishedAt(LocalDateTime.now());
        scanTaskRepository.save(task);

        scanIssueRepository.deleteByTaskId(task.getId());

        List<ScanIssue> issues = result.issues().stream().map(i -> {
            ScanIssue issue = new ScanIssue();
            issue.setTaskId(task.getId());
            issue.setFilePath(i.filePath());
            issue.setLineStart(i.lineStart());
            issue.setLineEnd(i.lineEnd());
            issue.setIssueType(i.issueType());
            issue.setSeverity(i.severity());
            issue.setDescription(i.description());
            issue.setSuggestion(i.suggestion());
            return issue;
        }).toList();

        scanIssueRepository.saveAll(issues);
    }

    /** 广播进度并更新 DB 状态 */
    private void progress(ScanTask task, String status, String step, int percent) {
        task.setStatus(status);
        scanTaskRepository.save(task);

        wsHandler.broadcast(WsMessage.builder()
                .type("SCAN_PROGRESS")
                .taskId(task.getId())
                .status(status)
                .step(step)
                .percent(percent)
                .build());
    }
}
