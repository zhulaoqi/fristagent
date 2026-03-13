package com.fristagent.scan;

import com.fristagent.scan.dto.ScanStatsDto;
import com.fristagent.scan.model.ScanIssue;
import com.fristagent.scan.model.ScanTask;
import com.fristagent.scan.repository.ScanIssueRepository;
import com.fristagent.scan.repository.ScanTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scans")
@RequiredArgsConstructor
public class ScanController {

    private final ScanTaskRepository scanTaskRepository;
    private final ScanIssueRepository scanIssueRepository;

    @GetMapping
    public Page<ScanTask> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "repoId", required = false) Long repoId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        if (status != null && repoId != null) {
            return scanTaskRepository.findByStatusAndRepoIdOrderByCreatedAtDesc(status, repoId, pageable);
        } else if (status != null) {
            return scanTaskRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (repoId != null) {
            return scanTaskRepository.findByRepoIdOrderByCreatedAtDesc(repoId, pageable);
        } else {
            return scanTaskRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScanTask> getById(@PathVariable("id") Long id) {
        return scanTaskRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/issues")
    public ResponseEntity<List<ScanIssue>> getIssues(@PathVariable("id") Long id) {
        if (!scanTaskRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(scanIssueRepository.findByTaskIdOrderBySeverityDesc(id));
    }

    @GetMapping("/stats")
    public ScanStatsDto stats() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        long totalScans = scanTaskRepository.count();
        long weeklyScans = scanTaskRepository.countSince(since);
        Double avgScore = scanTaskRepository.avgScore();

        // Build statusCounts map
        List<Object[]> statusRows = scanTaskRepository.countByStatus();
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Object[] row : statusRows) {
            statusCounts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        // Build scoreTrend list
        List<Object[]> trendRows = scanTaskRepository.dailyAvgScore(since);
        List<ScanStatsDto.DailyScoreTrend> scoreTrend = trendRows.stream()
                .map(row -> new ScanStatsDto.DailyScoreTrend(
                        String.valueOf(row[0]),
                        row[1] != null ? ((Number) row[1]).doubleValue() : null))
                .toList();

        List<Object[]> issueTypeRows = scanIssueRepository.countByIssueType();
        Map<String, Long> issueTypeCounts = new LinkedHashMap<>();
        for (Object[] row : issueTypeRows) {
            issueTypeCounts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        return new ScanStatsDto(totalScans, weeklyScans, avgScore, statusCounts, scoreTrend, issueTypeCounts);
    }
}
