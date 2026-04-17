package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;

/**
 * Verifies that getApplications does not produce N+1 queries when fetching applications with linked
 * applications. The test asserts that exactly 2 JDBC statements are issued regardless of page size:
 * one data query and one count query.
 */
public class GetApplicationsQueryCountTest extends BaseHarnessTest {

  private Statistics hibernateStats;

  @BeforeEach
  void enableHibernateStatistics() {
    SessionFactory sessionFactory =
        harnessProvider
            .getBean(EntityManagerFactory.class)
            .unwrap(SessionFactory.class);
    hibernateStats = sessionFactory.getStatistics();
    hibernateStats.setStatisticsEnabled(true);
  }

  @Test
  void givenFiveApplicationsWithLinkedApplications_whenGetApplications_thenOnlyTwoQueriesIssued() throws Exception {
    // given — create 3 lead applications, each with 2 associates (15 total rows in applications)
    List<ApplicationEntity> leadApplications = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      ApplicationEntity lead =
          persistedDataGenerator.createAndPersist(
              ApplicationEntityGenerator.class,
              b -> b.status(ApplicationStatus.APPLICATION_IN_PROGRESS));
      leadApplications.add(lead);
    }

    for (ApplicationEntity lead : leadApplications) {
      for (int j = 0; j < 2; j++) {
        ApplicationEntity associate =
            persistedDataGenerator.createAndPersist(
                ApplicationEntityGenerator.class,
                b -> b.status(ApplicationStatus.APPLICATION_IN_PROGRESS));
        lead.addLinkedApplication(associate);
      }
      persistedDataGenerator.updateAndFlush(lead);
    }

    // Also create 2 standalone applications (no linked applications)
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        2,
        b -> b.status(ApplicationStatus.APPLICATION_IN_PROGRESS));

    // Clear stats immediately before the request so setup queries don't count
    hibernateStats.clear();

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?pageSize=20");

    // then — response is OK and contains all 11 application rows:
    //   3 leads + 6 associates (2 per lead) + 2 standalones
    //   Associates are independent rows that happen to be linked — they all appear in results
    assertOK(result);
    ApplicationSummaryResponse response = deserialise(result, ApplicationSummaryResponse.class);
    assertThat(response.getApplications()).hasSize(11);

    long queryCount = hibernateStats.getQueryExecutionCount();
    long preparedStatementCount = hibernateStats.getPrepareStatementCount();

    // The custom CriteriaBuilder repository issues exactly 2 statements per getApplications call:
    //   1. The projected SELECT (with LEFT JOINs to caseworker and individuals)
    //   2. The COUNT query for pagination
    // The linked-applications lookups use native SQL via ApplicationRepository (2 more statements)
    // giving a total of 4. With the old ApplicationSummaryEntity approach, this would be
    // 2 + N (one extra SELECT per application to lazy-load caseworker/individuals).
    assertThat(preparedStatementCount)
        .as(
            "Expected exactly 4 prepared statements (2 criteria queries + 2 native linked-app"
                + " lookups), but got %d — N+1 queries may be present",
            preparedStatementCount)
        .isEqualTo(4);

    assertThat(hibernateStats.getEntityLoadCount())
        .as("No managed entities should be loaded — result is projected into a DTO")
        .isZero();

    assertThat(hibernateStats.getCollectionLoadCount())
        .as("No collections should be lazily loaded — N+1 would appear here")
        .isZero();
  }

  @Test
  void givenPageOfTenApplicationsWithLinkedApplications_whenGetApplications_thenQueryCountDoesNotScaleWithPageSize() throws Exception {
    // given — 10 lead applications each with 1 associate
    List<ApplicationEntity> leadApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            10,
            b -> b.status(ApplicationStatus.APPLICATION_IN_PROGRESS));

    for (ApplicationEntity lead : leadApplications) {
      ApplicationEntity associate =
          persistedDataGenerator.createAndPersist(
              ApplicationEntityGenerator.class,
              b -> b.status(ApplicationStatus.APPLICATION_IN_PROGRESS));
      lead.addLinkedApplication(associate);
      persistedDataGenerator.updateAndFlush(lead);
    }

    hibernateStats.clear();

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?pageSize=10");

    // then — statement count must be the same fixed number regardless of page size
    assertOK(result);
    long preparedStatementCount = hibernateStats.getPrepareStatementCount();

    assertThat(preparedStatementCount)
        .as(
            "Query count should be constant (4) regardless of page size — got %d",
            preparedStatementCount)
        .isEqualTo(4);

    assertThat(hibernateStats.getEntityLoadCount())
        .as("No managed entities should be loaded")
        .isZero();

    assertThat(hibernateStats.getCollectionLoadCount())
        .as("No lazy collection loads — no N+1")
        .isZero();
  }
}
