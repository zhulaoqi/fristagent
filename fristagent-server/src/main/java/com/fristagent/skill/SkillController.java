package com.fristagent.skill;

import com.fristagent.common.exception.BusinessException;
import com.fristagent.common.exception.ResourceNotFoundException;
import com.fristagent.skill.engine.SkillEngine;
import com.fristagent.skill.model.SkillRegistry;
import com.fristagent.skill.repository.SkillRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillRegistryRepository skillRegistryRepository;
    private final SkillEngine skillEngine;
    private final SkillInstaller skillInstaller;

    @GetMapping
    public List<SkillRegistry> listSkills() {
        return skillRegistryRepository.findAll();
    }

    @GetMapping("/active")
    public Map<String, String> getActiveSkill() {
        return Map.of("name", skillEngine.getActiveSkillName());
    }

    @PutMapping("/{name}/activate")
    public Map<String, String> activateSkill(@PathVariable("name") String name) {
        skillEngine.activateSkill(name);
        log.info("Skill activated: {}", name);
        return Map.of("active", name);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> uninstallSkill(@PathVariable("name") String name) {
        SkillRegistry skill = skillRegistryRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Skill", name));
        if ("BUILTIN".equals(skill.getSkillType())) {
            throw new BusinessException("BUILTIN_SKILL_READONLY", "内置 Skill 不允许删除");
        }
        if (Boolean.TRUE.equals(skill.getIsActive())) {
            throw new BusinessException("SKILL_ACTIVE_IN_USE", "当前激活的 Skill 不允许删除，请先切换到其他 Skill", HttpStatus.CONFLICT);
        }
        skillRegistryRepository.delete(skill);
        log.info("Skill uninstalled: {}", name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/install")
    public ResponseEntity<SkillRegistry> installSkill(@RequestBody Map<String, String> body) {
        String githubUrl = body.get("githubUrl");
        if (githubUrl == null || githubUrl.isBlank()) {
            throw new BusinessException("MISSING_PARAM", "githubUrl 不能为空");
        }
        try {
            SkillRegistry skill = skillInstaller.install(githubUrl);
            log.info("Skill installed: {}", skill.getName());
            return ResponseEntity.status(201).body(skill);
        } catch (IllegalStateException e) {
            throw new BusinessException("SKILL_ALREADY_EXISTS", e.getMessage(), HttpStatus.CONFLICT);
        } catch (Exception e) {
            throw new BusinessException("SKILL_INSTALL_FAILED", "安装失败: " + e.getMessage());
        }
    }
}
