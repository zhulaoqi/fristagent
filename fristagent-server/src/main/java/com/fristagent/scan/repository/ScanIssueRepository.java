package com.fristagent.scan.repository;

import com.fristagent.scan.model.ScanIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScanIssueRepository extends JpaRepository<ScanIssue, Long> {

    List<ScanIssue> findByTaskIdOrderBySeverityDesc(Long taskId);

    void deleteByTaskId(Long taskId);

    @Query("SELECT i.issueType, COUNT(i) FROM ScanIssue i GROUP BY i.issueType")
    List<Object[]> countByIssueType();
}
