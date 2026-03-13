package com.fristagent.notify;

import com.fristagent.agent.model.ReviewResult;
import com.fristagent.common.SmtpSenderFactory;
import com.fristagent.scan.model.ScanTask;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * SMTP 邮件通知。
 * SMTP 配置优先从 Redis (fristagent:notify:*) 读取，支持页面热更新，fallback 到 application.yml。
 * 每次发送前动态构建 JavaMailSenderImpl，无需重启即可生效新配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotifier {

    private static final String NOTIFY_PREFIX = "fristagent:notify:";

    private final StringRedisTemplate redis;
    private final SmtpSenderFactory smtpSenderFactory;

    @Value("${spring.mail.host:}")
    private String defaultSmtpHost;
    @Value("${spring.mail.port:587}")
    private int defaultSmtpPort;
    @Value("${spring.mail.username:}")
    private String defaultSmtpUsername;
    @Value("${spring.mail.password:}")
    private String defaultSmtpPassword;
    @Value("${fristagent.notify.mail-from-name:FristAgent}")
    private String defaultSmtpFromName;

    public void send(String toEmail, String toName, ScanTask task, ReviewResult result) {
        String smtpHost     = notifyConfig("smtpHost",     defaultSmtpHost);
        String smtpUsername = notifyConfig("smtpUsername", defaultSmtpUsername);
        String smtpPassword = notifyConfig("smtpPassword", defaultSmtpPassword);
        String fromName     = notifyConfig("smtpFromName", defaultSmtpFromName);
        int    smtpPort     = Integer.parseInt(notifyConfig("smtpPort", String.valueOf(defaultSmtpPort)));

        if (smtpHost.isBlank() || smtpUsername.isBlank()) {
            log.warn("SMTP not configured, skipping email to={}", toEmail);
            return;
        }

        try {
            JavaMailSenderImpl mailSender = smtpSenderFactory.build(smtpHost, smtpPort, smtpUsername, smtpPassword);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(smtpUsername, fromName);
            helper.setTo(toEmail);
            helper.setSubject(buildSubject(task, result));
            helper.setText(buildHtmlBody(task, result), true);

            mailSender.send(message);
            log.info("Email sent to={}, taskId={}", toEmail, task.getId());
        } catch (Exception e) {
            log.error("Failed to send email to={}", toEmail, e);
            throw new RuntimeException("Email send failed", e);
        }
    }

    // ---- Content builders ----

    private String buildSubject(ScanTask task, ReviewResult result) {
        String risk = result.score() < 50 ? "[高风险]" : result.score() < 75 ? "[中风险]" : "[低风险]";
        return risk + " PR Code Review - " + task.getPrTitle();
    }

    private String buildHtmlBody(ScanTask task, ReviewResult result) {
        String riskColor = result.score() < 50 ? "#e74c3c" : result.score() < 75 ? "#f39c12" : "#27ae60";
        String reportUrl = "http://fristagent.internal/scans/" + task.getId();

        StringBuilder issues = new StringBuilder();
        for (ReviewResult.Issue issue : result.issues()) {
            String severityColor = "HIGH".equals(issue.severity()) ? "#e74c3c"
                    : "MEDIUM".equals(issue.severity()) ? "#f39c12" : "#95a5a6";
            issues.append("""
                    <tr>
                      <td style="padding:8px;border-bottom:1px solid #eee">%s</td>
                      <td style="padding:8px;border-bottom:1px solid #eee">
                        <span style="color:%s;font-weight:bold">%s</span></td>
                      <td style="padding:8px;border-bottom:1px solid #eee">%s</td>
                      <td style="padding:8px;border-bottom:1px solid #eee">%s</td>
                    </tr>
                    """.formatted(
                    issue.filePath() != null ? issue.filePath() : "-",
                    severityColor, issue.severity(),
                    issue.description(),
                    issue.suggestion() != null ? issue.suggestion() : "-"
            ));
        }

        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family:Arial,sans-serif;max-width:800px;margin:0 auto;padding:20px">
                  <div style="background:%s;color:white;padding:20px;border-radius:8px 8px 0 0">
                    <h2 style="margin:0">🔍 PR Code Review Report</h2>
                  </div>
                  <div style="border:1px solid #ddd;border-top:none;padding:20px;border-radius:0 0 8px 8px">
                    <table style="width:100%%;border-collapse:collapse;margin-bottom:20px">
                      <tr><td style="padding:6px 0;color:#666">PR 标题</td>
                          <td style="padding:6px 0"><strong>%s</strong></td></tr>
                      <tr><td style="padding:6px 0;color:#666">作者</td>
                          <td style="padding:6px 0">%s</td></tr>
                      <tr><td style="padding:6px 0;color:#666">分支</td>
                          <td style="padding:6px 0">%s → %s</td></tr>
                      <tr><td style="padding:6px 0;color:#666">使用 Skill</td>
                          <td style="padding:6px 0">%s</td></tr>
                      <tr><td style="padding:6px 0;color:#666">综合评分</td>
                          <td style="padding:6px 0"><strong style="color:%s;font-size:20px">%d/100</strong></td></tr>
                    </table>
                    <h3>审查摘要</h3>
                    <p style="background:#f8f9fa;padding:12px;border-radius:4px">%s</p>
                    <h3>发现问题（共 %d 条）</h3>
                    <table style="width:100%%;border-collapse:collapse">
                      <thead>
                        <tr style="background:#f8f9fa">
                          <th style="padding:8px;text-align:left">文件</th>
                          <th style="padding:8px;text-align:left">严重程度</th>
                          <th style="padding:8px;text-align:left">问题描述</th>
                          <th style="padding:8px;text-align:left">修复建议</th>
                        </tr>
                      </thead>
                      <tbody>%s</tbody>
                    </table>
                    <div style="margin-top:24px;text-align:center">
                      <a href="%s" style="background:#3498db;color:white;padding:12px 24px;
                         border-radius:4px;text-decoration:none;margin-right:12px">
                        📊 查看完整报告
                      </a>
                      <a href="%s" style="background:#6c757d;color:white;padding:12px 24px;
                         border-radius:4px;text-decoration:none">
                        🔗 直达 PR
                      </a>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                riskColor,
                task.getPrTitle(), task.getPrAuthor(),
                task.getSourceRef(), task.getTargetBranch(),
                task.getSkillName(),
                riskColor, result.score(),
                result.summary(),
                result.issues().size(), issues,
                reportUrl, task.getPrUrl()
        );
    }

    private String notifyConfig(String field, String defaultValue) {
        String val = redis.opsForValue().get(NOTIFY_PREFIX + field);
        return (val != null && !val.isBlank()) ? val : (defaultValue != null ? defaultValue : "");
    }
}
