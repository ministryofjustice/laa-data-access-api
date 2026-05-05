package uk.gov.justice.laa.dstew.access.massgenerator.service;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataCleanupService {

  private final DataSource dataSource;

  public void cleanupAllTestData() {
    log.info("Starting cleanup of all test data...");

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(true);
      try (Statement stmt = conn.createStatement()) {
        // Terminate any other backends that might be holding locks
        stmt.execute(
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                + "WHERE datname = current_database() AND pid <> pg_backend_pid()");
        log.info("Terminated other connections");

        // Now truncate with no contention
        stmt.execute(
            "TRUNCATE TABLE certificates, merits_decisions, decisions, proceedings, "
                + "linked_individuals, applications, individuals, caseworkers CASCADE");
        log.info("Cleanup completed successfully");
      }
    } catch (Exception e) {
      throw new RuntimeException("Cleanup failed", e);
    }
  }
}
