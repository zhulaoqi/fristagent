package com.fristagent.scan.dto;

import java.util.List;
import java.util.Map;

public record ScanStatsDto(
        long totalScans,
        long weeklyScans,
        Double avgScore,
        Map<String, Long> statusCounts,
        List<DailyScoreTrend> scoreTrend,
        Map<String, Long> issueTypeCounts
) {
    public record DailyScoreTrend(String date, Double avgScore) {}
}
