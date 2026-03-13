package com.fristagent.scan.repository;

import com.fristagent.scan.model.ScanTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScanTaskRepository extends JpaRepository<ScanTask, Long> {

    List<ScanTask> findByRepoIdOrderByCreatedAtDesc(Long repoId);

    Optional<ScanTask> findByRepoIdAndPrNumber(Long repoId, String prNumber);

    List<ScanTask> findByStatusOrderByCreatedAtDesc(String status);

    // Pageable queries
    Page<ScanTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ScanTask> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<ScanTask> findByRepoIdOrderByCreatedAtDesc(Long repoId, Pageable pageable);

    Page<ScanTask> findByStatusAndRepoIdOrderByCreatedAtDesc(String status, Long repoId, Pageable pageable);

    // Stats queries
    @Query("SELECT COUNT(t) FROM ScanTask t WHERE t.createdAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT AVG(t.score) FROM ScanTask t WHERE t.status = 'DONE' AND t.score IS NOT NULL")
    Double avgScore();

    @Query("SELECT t.status, COUNT(t) FROM ScanTask t GROUP BY t.status")
    List<Object[]> countByStatus();

    @Query("SELECT FUNCTION('DATE', t.finishedAt), AVG(t.score) FROM ScanTask t " +
           "WHERE t.status = 'DONE' AND t.score IS NOT NULL AND t.finishedAt >= :since " +
           "GROUP BY FUNCTION('DATE', t.finishedAt) ORDER BY FUNCTION('DATE', t.finishedAt)")
    List<Object[]> dailyAvgScore(@Param("since") LocalDateTime since);
}
