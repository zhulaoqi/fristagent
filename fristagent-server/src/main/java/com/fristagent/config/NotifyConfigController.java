package com.fristagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fristagent.common.SmtpSenderFactory;
import com.fristagent.notify.dto.NotifyConfigDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/config/notify")
@RequiredArgsConstructor
public class NotifyConfigController {

    private static final String KEY_PREFIX = "fristagent:notify:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SmtpSenderFactory smtpSenderFactory;

    @Value("${fristagent.feishu.app-id:}")
    private String defaultFeishuAppId;
    @Value("${fristagent.feishu.app-secret:}")
    private String defaultFeishuAppSecret;
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

    @GetMapping
    public NotifyConfigDto getConfig() {
        return new NotifyConfigDto(
                getOrDefault("feishuAppId",     defaultFeishuAppId),
                getOrDefault("feishuAppSecret", defaultFeishuAppSecret),
                Boolean.parseBoolean(getOrDefault("feishuEnabled", "false")),
                getOrDefault("smtpHost",        defaultSmtpHost),
                Integer.parseInt(getOrDefault("smtpPort", String.valueOf(defaultSmtpPort))),
                getOrDefault("smtpUsername",    defaultSmtpUsername),
                getOrDefault("smtpPassword",    defaultSmtpPassword),
                getOrDefault("smtpFromName",    defaultSmtpFromName),
                Boolean.parseBoolean(getOrDefault("emailEnabled", "false"))
        );
    }

    @PostMapping
    public ResponseEntity<Void> updateConfig(@RequestBody NotifyConfigDto dto) {
        set("feishuAppId",     dto.feishuAppId());
        setSecret("feishuAppSecret", dto.feishuAppSecret());
        set("feishuEnabled",   String.valueOf(dto.feishuEnabled()));
        set("smtpHost",        dto.smtpHost());
        set("smtpPort",        dto.smtpPort() != null ? String.valueOf(dto.smtpPort()) : null);
        set("smtpUsername",    dto.smtpUsername());
        setSecret("smtpPassword", dto.smtpPassword());
        set("smtpFromName",    dto.smtpFromName());
        set("emailEnabled",    String.valueOf(dto.emailEnabled()));
        return ResponseEntity.ok().build();
    }

    /**
     * 验证飞书配置：获取 Token 后向指定 Open ID 发送测试卡片
     */
    @PostMapping("/test/feishu")
    public ResponseEntity<Map<String, Object>> testFeishu(@RequestBody Map<String, String> body) {
        String openId    = body.getOrDefault("openId", "").trim();
        String appId     = getOrDefault("feishuAppId",     defaultFeishuAppId);
        String appSecret = getOrDefault("feishuAppSecret", defaultFeishuAppSecret);

        if (appId.isBlank() || appSecret.isBlank()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "App ID 或 App Secret 未填写"));
        }
        if (openId.isBlank()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "请填写测试目标的飞书 Open ID"));
        }
        try {
            // 获取 tenant_access_token
            String tokenResp = RestClient.create("https://open.feishu.cn")
                    .post()
                    .uri("/open-apis/auth/v3/tenant_access_token/internal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("app_id", appId, "app_secret", appSecret))
                    .retrieve()
                    .body(String.class);

            if (tokenResp == null || !tokenResp.contains("tenant_access_token")) {
                return ResponseEntity.ok(Map.of("success", false, "message", "无法获取飞书 Token：" + tokenResp));
            }
            String token = objectMapper.readTree(tokenResp).path("tenant_access_token").asText();

            // 发送测试卡片
            String cardJson = objectMapper.writeValueAsString(buildTestFeishuCard());
            Map<String, Object> msgBody = new LinkedHashMap<>();
            msgBody.put("receive_id", openId);
            msgBody.put("msg_type",   "interactive");
            msgBody.put("content",    cardJson);

            String sendResp = RestClient.create("https://open.feishu.cn")
                    .post()
                    .uri("/open-apis/im/v1/messages?receive_id_type=open_id")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(msgBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                    .body(String.class);

            if (sendResp != null && sendResp.contains("\"code\":0")) {
                return ResponseEntity.ok(Map.of("success", true, "message", "测试卡片已成功发送至 " + openId));
            }
            // 解析飞书错误码给出友好提示
            String feishuMsg = sendResp != null ? sendResp : "无响应";
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(sendResp);
                int code = node.path("code").asInt();
                String msg = node.path("msg").asText();
                feishuMsg = switch (code) {
                    case 99992361 -> "open_id 跨应用：该 open_id 属于其他应用，请使用当前自建应用下的 open_id（飞书开发者后台 → 成员管理 → 搜索用户 → 复制 open_id）";
                    case 230013   -> "机器人未加入该用户会话，请先在飞书向该机器人发一条消息";
                    case 99991400 -> "tenant_access_token 无效，请检查 App ID / App Secret";
                    default       -> "飞书返回错误 [" + code + "]: " + msg;
                };
            } catch (Exception ignored) {}
            return ResponseEntity.ok(Map.of("success", false, "message", feishuMsg));
        } catch (Exception e) {
            log.warn("Feishu test failed", e);
            return ResponseEntity.ok(Map.of("success", false, "message", "请求失败：" + e.getMessage()));
        }
    }

    /**
     * 发送测试邮件（与正式邮件同款 HTML 模板，使用示例数据）
     */
    @PostMapping("/test/email")
    public ResponseEntity<Map<String, Object>> testEmail(@RequestBody Map<String, String> body) {
        String toEmail   = body.get("email");
        String smtpHost  = getOrDefault("smtpHost",     defaultSmtpHost);
        String username  = getOrDefault("smtpUsername", defaultSmtpUsername);
        String password  = getOrDefault("smtpPassword", defaultSmtpPassword);
        String fromName  = getOrDefault("smtpFromName", defaultSmtpFromName);
        int    port      = Integer.parseInt(getOrDefault("smtpPort", String.valueOf(defaultSmtpPort)));

        if (smtpHost.isBlank() || username.isBlank()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "SMTP 服务器或用户名未配置"));
        }
        if (toEmail == null || toEmail.isBlank()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "请填写收件人邮箱"));
        }
        try {
            JavaMailSenderImpl sender = smtpSenderFactory.build(smtpHost, port, username, password);
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(username, fromName);
            helper.setTo(toEmail);
            helper.setSubject("[低风险][测试] PR Code Review - feat: 示例功能迭代");
            helper.setText(buildTestEmailHtml(), true);
            sender.send(msg);
            return ResponseEntity.ok(Map.of("success", true, "message", "测试邮件已发送至 " + toEmail));
        } catch (Exception e) {
            log.warn("Test email failed", e);
            return ResponseEntity.ok(Map.of("success", false, "message", "发送失败：" + e.getMessage()));
        }
    }

    // ---- Test content builders ----

    private Map<String, Object> buildTestFeishuCard() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", Map.of("wide_screen_mode", true));

        Map<String, Object> title = new LinkedHashMap<>();
        title.put("tag", "plain_text");
        title.put("content", "✅ FristAgent 通知测试");
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", title);
        header.put("template", "green");
        card.put("header", header);

        List<Object> elements = new ArrayList<>();

        Map<String, Object> desc = new LinkedHashMap<>();
        desc.put("tag", "div");
        Map<String, Object> descText = new LinkedHashMap<>();
        descText.put("tag", "lark_md");
        descText.put("content", "飞书通知配置验证成功！\n\n当有新 PR 扫描完成后，FristAgent 将自动向配置的接收人发送代码审查报告卡片。");
        desc.put("text", descText);
        elements.add(desc);

        elements.add(Map.of("tag", "hr"));

        Map<String, Object> note = new LinkedHashMap<>();
        note.put("tag", "note");
        List<Object> noteEls = new ArrayList<>();
        Map<String, Object> noteText = new LinkedHashMap<>();
        noteText.put("tag", "plain_text");
        noteText.put("content", "由 FristAgent 自动发送 · 收到此消息说明通知配置正确");
        noteEls.add(noteText);
        note.put("elements", noteEls);
        elements.add(note);

        card.put("elements", elements);
        return card;
    }

    private String buildTestEmailHtml() {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f4f6f9;font-family:Arial,sans-serif">
                  <div style="max-width:680px;margin:32px auto;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08)">
                    <!-- Header -->
                    <div style="background:#27ae60;padding:28px 32px">
                      <div style="color:rgba(255,255,255,0.85);font-size:12px;font-weight:600;letter-spacing:0.08em;text-transform:uppercase;margin-bottom:6px">FristAgent · Code Review</div>
                      <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;line-height:1.3">feat: 示例功能迭代</h1>
                      <div style="margin-top:10px;display:inline-block;background:rgba(255,255,255,0.2);border:1px solid rgba(255,255,255,0.35);border-radius:20px;padding:3px 12px;color:#ffffff;font-size:12px;font-weight:600">🟢 低风险 · 85/100</div>
                    </div>
                    <!-- Meta -->
                    <div style="padding:24px 32px 0">
                      <table style="width:100%%;border-collapse:collapse">
                        <tr>
                          <td style="padding:7px 0;color:#8a9bb0;font-size:13px;width:90px">PR 编号</td>
                          <td style="padding:7px 0;color:#1a2035;font-size:13px;font-weight:500">#42</td>
                          <td style="padding:7px 0;color:#8a9bb0;font-size:13px;width:90px">提交作者</td>
                          <td style="padding:7px 0;color:#1a2035;font-size:13px;font-weight:500">developer</td>
                        </tr>
                        <tr>
                          <td style="padding:7px 0;color:#8a9bb0;font-size:13px">合并方向</td>
                          <td style="padding:7px 0;color:#1a2035;font-size:13px;font-weight:500" colspan="3">feature/example → main</td>
                        </tr>
                        <tr>
                          <td style="padding:7px 0;color:#8a9bb0;font-size:13px">使用 Skill</td>
                          <td style="padding:7px 0;color:#1a2035;font-size:13px;font-weight:500" colspan="3">universal-code-reviewer</td>
                        </tr>
                        <tr>
                          <td style="padding:7px 0;color:#8a9bb0;font-size:13px">综合评分</td>
                          <td style="padding:7px 0;font-size:22px;font-weight:700;color:#27ae60" colspan="3">85 <span style="font-size:13px;color:#8a9bb0;font-weight:400">/ 100</span></td>
                        </tr>
                      </table>
                    </div>
                    <div style="margin:0 32px;border-top:1px solid #eef1f6"></div>
                    <!-- Summary -->
                    <div style="padding:20px 32px 0">
                      <div style="font-size:13px;font-weight:600;color:#8a9bb0;text-transform:uppercase;letter-spacing:0.06em;margin-bottom:10px">审查摘要</div>
                      <p style="margin:0;color:#3d4a5c;font-size:14px;line-height:1.7;background:#f8fafc;border-left:3px solid #27ae60;padding:12px 16px;border-radius:0 6px 6px 0">整体代码质量良好，逻辑清晰，命名规范。发现 1 处潜在空指针风险和 1 处可优化的 SQL 查询，建议修复后合入。</p>
                    </div>
                    <!-- Issues -->
                    <div style="padding:20px 32px 0">
                      <div style="font-size:13px;font-weight:600;color:#8a9bb0;text-transform:uppercase;letter-spacing:0.06em;margin-bottom:12px">发现问题（共 2 条）</div>
                      <table style="width:100%%;border-collapse:collapse;font-size:13px">
                        <thead>
                          <tr style="background:#f8fafc">
                            <th style="padding:10px 12px;text-align:left;color:#8a9bb0;font-weight:600;border-bottom:1px solid #eef1f6">文件</th>
                            <th style="padding:10px 12px;text-align:left;color:#8a9bb0;font-weight:600;border-bottom:1px solid #eef1f6;white-space:nowrap">严重程度</th>
                            <th style="padding:10px 12px;text-align:left;color:#8a9bb0;font-weight:600;border-bottom:1px solid #eef1f6">问题描述</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr>
                            <td style="padding:10px 12px;color:#3d4a5c;border-bottom:1px solid #f0f2f5;font-family:monospace;font-size:12px">UserService.java</td>
                            <td style="padding:10px 12px;border-bottom:1px solid #f0f2f5"><span style="color:#f39c12;font-weight:600;font-size:12px">MEDIUM</span></td>
                            <td style="padding:10px 12px;color:#3d4a5c;border-bottom:1px solid #f0f2f5">getUser() 返回值未做空值检查，可能抛出 NullPointerException</td>
                          </tr>
                          <tr>
                            <td style="padding:10px 12px;color:#3d4a5c;font-family:monospace;font-size:12px">OrderRepository.java</td>
                            <td style="padding:10px 12px"><span style="color:#95a5a6;font-weight:600;font-size:12px">LOW</span></td>
                            <td style="padding:10px 12px;color:#3d4a5c">IN 查询列表较大时建议改用批量分页查询以避免性能问题</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                    <!-- Actions -->
                    <div style="padding:24px 32px 32px;text-align:center">
                      <a href="#" style="display:inline-block;background:#3498db;color:#ffffff;padding:11px 24px;border-radius:6px;text-decoration:none;font-size:13px;font-weight:600;margin-right:10px">📊 查看完整报告</a>
                      <a href="#" style="display:inline-block;background:#f8fafc;color:#3d4a5c;border:1px solid #dde3ec;padding:11px 24px;border-radius:6px;text-decoration:none;font-size:13px;font-weight:600">🔗 直达 PR</a>
                    </div>
                    <!-- Footer -->
                    <div style="background:#f8fafc;border-top:1px solid #eef1f6;padding:14px 32px;text-align:center;color:#b0bac9;font-size:12px">
                      此邮件由 FristAgent 自动发送 · 这是一封测试邮件，用于验证通知配置是否正确
                    </div>
                  </div>
                </body>
                </html>
                """;
    }

    // ---- Helpers ----

    private String getOrDefault(String field, String defaultValue) {
        String val = redis.opsForValue().get(KEY_PREFIX + field);
        return (val != null && !val.isBlank()) ? val : (defaultValue != null ? defaultValue : "");
    }

    private void set(String field, String value) {
        if (value != null) {
            redis.opsForValue().set(KEY_PREFIX + field, value);
        }
    }

    /** 密码/Secret 类字段：空值不覆盖已存储的值 */
    private void setSecret(String field, String value) {
        if (value != null && !value.isBlank()) {
            redis.opsForValue().set(KEY_PREFIX + field, value);
        }
    }
}
