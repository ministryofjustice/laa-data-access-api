package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import uk.gov.justice.laa.dstew.access.DataAccessServiceAxonApplication;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/**
 * Integration tests for {@link LinkedGroupAssignmentEventHandler}.
 *
 * <p>Verifies that:
 *
 * <ul>
 *   <li>Spring context loads successfully with all beans configured
 *   <li>LinkedGroupAssignmentEventHandler is properly registered
 *   <li>LinkedGroupAssignmentRulesService is available and wired
 *   <li>LinkedGroupAssignmentDispatcher is available and wired
 * </ul>
 *
 * <p>Uses in-memory H2 database. Note: Full command execution tests are covered by comprehensive
 * unit tests (LinkedGroupAssignmentRulesServiceTest, LinkedGroupAssignmentDispatcherTest,
 * LinkedGroupAssignmentEventHandlerTest). This integration test focuses on Spring context setup.
 */
@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.datasource.url=jdbc:h2:mem:linked-group-assignment;DB_CLOSE_DELAY=-1",
    })
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
@DisplayName("LinkedGroupAssignment Spring Context Integration Tests")
class LinkedGroupAssignmentIntegrationTest {

  @Autowired(required = false)
  private CommandGateway commandGateway;

  @Autowired(required = false)
  private LinkedGroupAssignmentEventHandler eventHandler;

  @Autowired(required = false)
  private LinkedGroupAssignmentRulesService rulesService;

  @Autowired(required = false)
  private LinkedGroupAssignmentDispatcher dispatcher;

  @Test
  @DisplayName("Spring context loads successfully with all required beans")
  void givenSpringBootTest_whenContextLoads_thenAllBeansAvailable() {
    assertThat(commandGateway)
        .as("CommandGateway should be autowired from Axon framework")
        .isNotNull();
  }

  @Test
  @DisplayName("LinkedGroupAssignmentEventHandler bean is registered and available")
  void givenEventHandlerDefined_whenSpringContextLoads_thenHandlerAvailable() {
    assertThat(eventHandler)
        .as("LinkedGroupAssignmentEventHandler should be registered in Spring context")
        .isNotNull();
  }

  @Test
  @DisplayName("LinkedGroupAssignmentRulesService bean is registered and available")
  void givenRulesServiceDefined_whenSpringContextLoads_thenServiceAvailable() {
    assertThat(rulesService)
        .as("LinkedGroupAssignmentRulesService should be registered in Spring context")
        .isNotNull();
  }

  @Test
  @DisplayName("LinkedGroupAssignmentDispatcher bean is registered and available")
  void givenDispatcherDefined_whenSpringContextLoads_thenDispatcherAvailable() {
    assertThat(dispatcher)
        .as("LinkedGroupAssignmentDispatcher should be registered in Spring context")
        .isNotNull();
  }

  @Test
  @DisplayName("EventHandler has @Namespace annotation for synchronous execution")
  void givenEventHandler_whenInspected_thenHasNamespaceAnnotation() {
    assertThat(LinkedGroupAssignmentEventHandler.class)
        .as(
            "LinkedGroupAssignmentEventHandler should have @Namespace annotation for subscribing processor mode")
        .hasAnnotation(org.axonframework.messaging.core.annotation.Namespace.class);
  }

  @Test
  @DisplayName("All required beans are properly autowired and interconnected")
  void givenAllBeansLoaded_whenInspected_thenProperlyWired() {
    // Verify all beans are available
    assertThat(commandGateway).isNotNull();
    assertThat(eventHandler).isNotNull();
    assertThat(rulesService).isNotNull();
    assertThat(dispatcher).isNotNull();

    // Verify beans are of correct type
    assertThat(eventHandler).isInstanceOf(LinkedGroupAssignmentEventHandler.class);
    assertThat(rulesService).isInstanceOf(LinkedGroupAssignmentRulesService.class);
    assertThat(dispatcher).isInstanceOf(LinkedGroupAssignmentDispatcher.class);
  }
}
