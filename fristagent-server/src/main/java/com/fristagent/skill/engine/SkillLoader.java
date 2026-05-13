package com.fristagent.skill.engine;

import com.fristagent.diff.model.DiffContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * AgentScope-style 渐进式 Skill 披露加载器。
 *
 * <p>披露策略（三阶段，参照 AgentScope Java 的 Progressive Disclosure 设计）：
 * <ol>
 *   <li><b>Metadata</b>（~100 tokens）: skill.yaml 元数据，始终最先加载，用于展示 / 路由决策</li>
 *   <li><b>Core + Output-Format</b>（~300 tokens）: 所有扫描必须加载的核心指令和 JSON 输出格式</li>
 *   <li><b>Language Sections</b>（按需，~200-400 tokens/语言）:
 *       根据 diff 中实际出现的文件类型，仅加载对应语言的详细规则，
 *       未出现的语言不进入 context window，节省 token 同时减少干扰</li>
 * </ol>
 *
 * <p>目录约定（classpath 或 skillDataDir）：
 * <pre>
 * {skillName}/
 * ├── skill.yaml               ← 元数据
 * ├── system-prompt.md         ← 兼容旧格式（无 sections/ 时 fallback）
 * └── sections/
 *     ├── core.md              ← 阶段 2：始终加载
 *     ├── output-format.md     ← 阶段 2：始终加载
 *     └── lang/
 *         ├── java.md          ← 阶段 3：按需
 *         ├── python.md
 *         ├── javascript.md    ← .js / .ts / .vue / .jsx / .tsx
 *         └── go.md
 * </pre>
 */
@Slf4j
@Component
public class SkillLoader {

    /** 文件扩展名 → sections/lang/ 文件名 映射 */
    private static final Map<String, String> EXT_TO_LANG = Map.ofEntries(
            Map.entry("java",   "java"),
            Map.entry("kt",     "java"),   // Kotlin — reuse Java rules for JVM idioms
            Map.entry("py",     "python"),
            Map.entry("js",     "javascript"),
            Map.entry("ts",     "javascript"),
            Map.entry("jsx",    "javascript"),
            Map.entry("tsx",    "javascript"),
            Map.entry("vue",    "javascript"),
            Map.entry("go",     "go"),
            Map.entry("rs",     "rust"),
            Map.entry("cs",     "csharp")
    );

    @Value("${fristagent.skill.data-dir}")
    private String skillDataDir;

    // ─────────────────────────────────────────────────────────────
    // 公共 API
    // ─────────────────────────────────────────────────────────────

    /**
     * 阶段 1: 仅加载元数据（skill.yaml），约 100 tokens。
     * 供 UI 展示 / Skill 路由决策时使用，不进入 LLM context。
     */
    public String loadMeta(String skillName) {
        return loadSection(skillName, "skill.yaml");
    }

    /**
     * 阶段 2 + 3: 按照 diff 上下文构建最小化 system-prompt。
     *
     * <ul>
     *   <li>始终包含 sections/core.md + sections/output-format.md</li>
     *   <li>仅追加 diff 中实际出现文件类型对应的 lang section</li>
     *   <li>若无 sections/ 目录则 fallback 到 system-prompt.md（向后兼容）</li>
     * </ul>
     *
     * @param skillName     active skill 名称
     * @param diff          当前 PR 的 diff 上下文
     * @return 组装好的 system-prompt 字符串
     */
    public String buildContextualPrompt(String skillName, DiffContext diff) {
        // Fallback: 没有 sections/ 时直接用旧的整体文件
        String core = loadSection(skillName, "sections/core.md");
        if (core == null) {
            log.info("[SkillLoader] skill={} has no sections/, falling back to system-prompt.md", skillName);
            return requireSection(skillName, "system-prompt.md");
        }

        // 检测 diff 中出现的语言
        Set<String> detectedLangs = detectLanguages(diff);
        log.info("[SkillLoader] skill={} | diff files={} | detected langs={}",
                skillName, diff.files().size(), detectedLangs);

        StringBuilder prompt = new StringBuilder();

        // 阶段 2: Core（始终加载）
        prompt.append(core);

        // 阶段 3: 按需语言 section
        List<String> loadedLangs = new ArrayList<>();
        for (String lang : detectedLangs) {
            String langSection = loadSection(skillName, "sections/lang/" + lang + ".md");
            if (langSection != null) {
                prompt.append("\n\n").append(langSection);
                loadedLangs.add(lang);
            }
        }

        // 输出格式（始终在末尾加载，保证 LLM 最后看到格式约束）
        String outputFormat = loadSection(skillName, "sections/output-format.md");
        if (outputFormat != null) {
            prompt.append("\n\n").append(outputFormat);
        }

        int totalChars = prompt.length();
        log.info("[SkillLoader] skill={} | loaded lang sections={} | total prompt chars={} (~{} tokens)",
                skillName, loadedLangs, totalChars, totalChars / 4);

        return prompt.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // 内部工具
    // ─────────────────────────────────────────────────────────────

    /**
     * 加载单个 section 文件；优先自定义目录，fallback classpath。
     * 文件不存在时返回 null（区别于加载失败）。
     */
    public String loadSection(String skillName, String relativePath) {
        // 1. 自定义目录
        Path customPath = Path.of(skillDataDir, skillName, relativePath);
        if (Files.exists(customPath)) {
            try {
                return Files.readString(customPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("[SkillLoader] Failed to read custom section: {}", customPath, e);
            }
        }
        // 2. Classpath
        String cp = "skills/" + skillName + "/" + relativePath;
        ClassPathResource resource = new ClassPathResource(cp);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[SkillLoader] Failed to read classpath section: {}", cp, e);
            return null;
        }
    }

    /** 加载 section，文件不存在时抛出异常（关键文件） */
    private String requireSection(String skillName, String relativePath) {
        String content = loadSection(skillName, relativePath);
        if (content == null) {
            throw new IllegalStateException(
                    "Required skill section not found: " + skillName + "/" + relativePath);
        }
        return content;
    }

    /**
     * 从 diff 文件路径中提取语言集合（去重，有序）。
     * 返回的 lang key 对应 sections/lang/{lang}.md 文件名。
     */
    private Set<String> detectLanguages(DiffContext diff) {
        Set<String> langs = new LinkedHashSet<>();
        for (DiffContext.FileDiff file : diff.files()) {
            String path = file.path().toLowerCase();
            int dot = path.lastIndexOf('.');
            if (dot >= 0) {
                String ext = path.substring(dot + 1);
                String lang = EXT_TO_LANG.get(ext);
                if (lang != null) {
                    langs.add(lang);
                }
            }
        }
        return langs;
    }
}
