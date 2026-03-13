package com.fristagent.skill;

import com.fristagent.skill.model.SkillRegistry;
import com.fristagent.skill.repository.SkillRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillInstaller {

    private final SkillRegistryRepository skillRegistryRepository;

    @Value("${fristagent.skill.data-dir}")
    private String skillDataDir;

    /**
     * 从 GitHub 仓库 URL 安装 Skill
     *
     * @param githubUrl 例如 https://github.com/owner/repo-name
     * @return 安装好的 SkillRegistry 对象
     */
    public SkillRegistry install(String githubUrl) throws IOException, InterruptedException {
        // 1. 构造 ZIP 下载 URL
        String zipUrl = toZipUrl(githubUrl);
        log.info("Installing skill from: {}", zipUrl);

        // 2. 下载 ZIP 到临时文件
        Path tempFile = Files.createTempFile("skill-install-", ".zip");
        try {
            downloadZip(zipUrl, tempFile);

            // 3. 解压，查找 skill.yaml 和 system-prompt.md
            SkillFiles skillFiles = extractSkillFiles(tempFile);

            // 4. 解析 skill.yaml
            Map<String, String> meta = parseYaml(skillFiles.skillYaml);
            String skillName = meta.get("name");
            if (skillName == null || skillName.isBlank()) {
                throw new IllegalArgumentException("skill.yaml must contain a 'name' field");
            }
            skillName = skillName.trim();

            // 5. 检查是否已存在
            if (skillRegistryRepository.existsByName(skillName)) {
                throw new IllegalStateException("Skill already installed: " + skillName);
            }

            // 6. 拷贝文件到 skillDataDir/{skillName}/
            Path targetDir = Path.of(skillDataDir, skillName);
            Files.createDirectories(targetDir);
            Files.writeString(targetDir.resolve("skill.yaml"), skillFiles.skillYaml);
            if (skillFiles.systemPrompt != null) {
                Files.writeString(targetDir.resolve("system-prompt.md"), skillFiles.systemPrompt);
            }
            log.info("Skill files saved to: {}", targetDir);

            // 7. 插入 DB 记录
            SkillRegistry registry = new SkillRegistry();
            registry.setName(skillName);
            registry.setDisplayName(meta.getOrDefault("display_name", skillName));
            registry.setVersion(meta.getOrDefault("version", "1.0.0"));
            registry.setDescription(meta.getOrDefault("description", ""));
            registry.setSourceUrl(githubUrl);
            registry.setSkillType("EXTERNAL");
            registry.setIsActive(false);
            SkillRegistry saved = skillRegistryRepository.save(registry);
            log.info("Skill registered: name={}, id={}", skillName, saved.getId());
            return saved;

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private String toZipUrl(String githubUrl) {
        // 去掉末尾斜杠
        String url = githubUrl.endsWith("/") ? githubUrl.substring(0, githubUrl.length() - 1) : githubUrl;
        return url + "/archive/refs/heads/main.zip";
    }

    private void downloadZip(String zipUrl, Path dest) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(zipUrl))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalArgumentException(
                    "Failed to download ZIP from " + zipUrl + ", HTTP status: " + response.statusCode());
        }

        try (InputStream is = response.body()) {
            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Downloaded ZIP to: {}", dest);
    }

    private SkillFiles extractSkillFiles(Path zipFile) throws IOException {
        String skillYaml = null;
        String systemPrompt = null;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // 匹配根目录或任意子目录下的 skill.yaml / system-prompt.md
                if (!entry.isDirectory()) {
                    String fileName = Path.of(name).getFileName().toString();
                    if ("skill.yaml".equals(fileName) && skillYaml == null) {
                        skillYaml = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    } else if ("system-prompt.md".equals(fileName) && systemPrompt == null) {
                        systemPrompt = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
                zis.closeEntry();
            }
        }

        if (skillYaml == null) {
            throw new IllegalArgumentException("skill.yaml not found in the downloaded ZIP");
        }

        return new SkillFiles(skillYaml, systemPrompt);
    }

    /**
     * 简单解析 key: value 格式的 YAML
     */
    private Map<String, String> parseYaml(String content) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                map.put(key, value);
            }
        }
        return map;
    }

    private record SkillFiles(String skillYaml, String systemPrompt) {}
}
