package com.fristagent.skill.engine;

import com.fristagent.common.ws.ScanWebSocketHandler;
import com.fristagent.common.ws.WsMessage;
import com.fristagent.skill.model.SkillRegistry;
import com.fristagent.skill.repository.SkillRegistryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Skill 引擎：负责加载 Skill 的 system-prompt，支持热切换。
 * active_skill 存储在 Redis，全局生效，无需重启。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillEngine {

    private static final String ACTIVE_SKILL_KEY = "fristagent:active_skill";
    private static final String[] BUILTIN_SKILLS = {
            "universal-code-reviewer",
            "langchain-cr-pro",
            "team-style-enforcer"
    };

    private final StringRedisTemplate redis;
    private final SkillRegistryRepository skillRegistryRepository;
    private final ScanWebSocketHandler wsHandler;

    @Value("${fristagent.skill.data-dir}")
    private String skillDataDir;

    /**
     * 启动时同步 Redis active_skill（以 DB 为准）
     */
    @PostConstruct
    public void init() {
        skillRegistryRepository.findByIsActiveTrue().ifPresent(skill -> {
            redis.opsForValue().set(ACTIVE_SKILL_KEY, skill.getName());
            log.info("Active skill initialized: {}", skill.getName());
        });
    }

    /**
     * 获取当前激活的 Skill 名称
     */
    public String getActiveSkillName() {
        String name = redis.opsForValue().get(ACTIVE_SKILL_KEY);
        if (name == null) {
            // fallback 到 DB
            return skillRegistryRepository.findByIsActiveTrue()
                    .map(SkillRegistry::getName)
                    .orElse(BUILTIN_SKILLS[0]);
        }
        return name;
    }

    /**
     * 加载激活 Skill 的 system-prompt 内容
     */
    public String loadActiveSystemPrompt() {
        return loadSystemPrompt(getActiveSkillName());
    }

    /**
     * 加载指定 Skill 的 system-prompt
     */
    public String loadSystemPrompt(String skillName) {
        // 先尝试从自定义目录加载
        Path customPath = Path.of(skillDataDir, skillName, "system-prompt.md");
        if (Files.exists(customPath)) {
            try {
                String content = Files.readString(customPath, StandardCharsets.UTF_8);
                log.info("Skill [{}] loaded from custom dir: {}, length={}", skillName, customPath, content.length());
                return content;
            } catch (IOException e) {
                log.error("Failed to read custom skill prompt: {}", customPath, e);
            }
        }
        // 内置 Skill 从 classpath 加载
        String classpathLocation = "skills/" + skillName + "/system-prompt.md";
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            try (InputStream is = resource.getInputStream()) {
                String content = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                log.info("Skill [{}] loaded from classpath: {}, length={}", skillName, classpathLocation, content.length());
                return content;
            }
        } catch (IOException e) {
            log.error("Skill not found: {} (tried custom={}, classpath={})", skillName, customPath, classpathLocation);
            throw new IllegalStateException("Skill not found: " + skillName, e);
        }
    }

    /**
     * 热切换 active skill，立即全局生效
     */
    @Transactional
    public void activateSkill(String skillName) {
        SkillRegistry skill = skillRegistryRepository.findByName(skillName)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillName));

        // DB 更新
        skillRegistryRepository.deactivateAll();
        skill.setIsActive(true);
        skillRegistryRepository.save(skill);

        // Redis 更新（立即全局生效）
        redis.opsForValue().set(ACTIVE_SKILL_KEY, skillName);
        log.info("Skill activated: {}", skillName);

        // WebSocket 广播，通知所有前端页面同步更新
        wsHandler.broadcast(WsMessage.builder()
                .type("SKILL_SWITCHED")
                .skillName(skillName)
                .build());
    }
}
