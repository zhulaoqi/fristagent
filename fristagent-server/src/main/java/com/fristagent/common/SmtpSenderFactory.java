package com.fristagent.common;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 根据运行时配置动态构建 {@link JavaMailSenderImpl}。
 * 支持热更新：每次发送前调用 {@link #build} 即可使用最新配置，无需重启。
 * <p>
 * 端口 465 → 直接 SSL；其余端口 → STARTTLS。
 */
@Component
public class SmtpSenderFactory {

    public JavaMailSenderImpl build(String host, int port, String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol",    "smtp");
        props.put("mail.smtp.auth",             "true");
        if (port == 465) {
            props.put("mail.smtp.ssl.enable",   "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.ssl.trust",        host);
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout",          "15000");
        props.put("mail.smtp.writetimeout",     "10000");
        return sender;
    }
}
