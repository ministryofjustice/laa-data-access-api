package uk.gov.justice.laa.dstew.access.command.worklist;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemCoordinator;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignWorkItemCoordinator;
import uk.gov.justice.laa.dstew.access.utils.BaseSecuredUseCaseTest;
import uk.gov.justice.laa.dstew.access.utils.TestSecurityConfig;

@SpringBootTest(
    classes = {
      AssignWorkItemCoordinator.class,
      UnassignWorkItemCoordinator.class,
      TestSecurityConfig.class
    })
@ImportAutoConfiguration(
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
class WorkItemAssignmentCoordinatorsSecurityTest extends BaseSecuredUseCaseTest {
  @Autowired private AssignWorkItemCoordinator assignCoordinator;
  @Autowired private UnassignWorkItemCoordinator unassignCoordinator;

  @MockitoBean private WorkItemRouteResolver routeResolver;
  @MockitoBean private RetryingCommandDispatcher dispatcher;

  @Test
  void givenNoRole_whenAssignmentCoordinatorsCalled_thenAccessDenied() {
    setSecurityContext(NO_ROLE);

    assertDenied();

    verifyNoInteractions(routeResolver, dispatcher);
  }

  @Test
  void givenBlankName_whenAssignmentCoordinatorsCalled_thenAccessDenied() {
    setSecurityContextWithName(" ", CASEWORKER_ROLE);

    assertDenied();

    verifyNoInteractions(routeResolver, dispatcher);
  }

  private void assertDenied() {
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> assignCoordinator.handle(assignCommand()))
        .withMessageContaining("Access Denied");
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> unassignCoordinator.handle(unassignCommand()))
        .withMessageContaining("Access Denied");
  }

  private AssignWorkItemCommand assignCommand() {
    UUID workItemId = UUID.randomUUID();
    return new AssignWorkItemCommand(
        workItemId,
        UUID.randomUUID(),
        new ExpectedAssignment(workItemId, 0L),
        "{}",
        "Assigned",
        Instant.now());
  }

  private UnassignWorkItemCommand unassignCommand() {
    UUID workItemId = UUID.randomUUID();
    return new UnassignWorkItemCommand(
        workItemId, new ExpectedAssignment(workItemId, 0L), "{}", "Unassigned", Instant.now());
  }
}
