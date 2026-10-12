package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.EstablishLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.RemoveApplicationFromLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationProjection;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexProjection;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadRepository;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import({
  TestJwtDecoderConfig.class,
  ApplicationProjectionSequencingIntegrationTest.GateConfig.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationProjectionSequencingIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ApplicationReadRepository applications;
  @Autowired private ApplicationListIndexReadRepository listIndex;
  @Autowired private CommandGateway commands;
  @Autowired private ProjectionGate gate;

  @Test
  void givenCreatedAndGroupEventsRace_whenProcessed_thenMembershipRowIsCreated()
      throws InterruptedException {
    UUID applicationId = new UUID(0, 1);
    UUID leadId = UUID.randomUUID();
    UUID groupId = new UUID(0, 2);
    createApplication(leadId);

    gate.arm(
        ApplicationCreatedEvent.class,
        applicationId,
        LinkedApplicationGroupCreatedEvent.class,
        groupId);
    try {
      postApplication(applicationId);
      gate.awaitBlocked();
      commands.sendAndWait(
          new EstablishLinkedApplicationGroupCommand(
              groupId, leadId, List.of(leadId, applicationId), Instant.now()));
      assertThat(gate.observedWhileBlocked()).isFalse();
    } finally {
      gate.release();
    }

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(applications.findById(applicationId).orElseThrow().getLinkedGroupId())
                    .isEqualTo(groupId));
  }

  @Test
  void givenNoteAndMembershipEventsRace_whenProcessed_thenMembershipIsRetained()
      throws InterruptedException {
    UUID applicationId = new UUID(0, 3);
    UUID leadId = UUID.randomUUID();
    UUID groupId = new UUID(0, 4);
    createApplication(applicationId);
    createApplication(leadId);

    gate.arm(
        NoteCreatedEvent.class, applicationId, LinkedApplicationGroupCreatedEvent.class, groupId);
    try {
      commands.sendAndWait(
          new CreateNoteCommand(
              applicationId, "test note", "{\"note\":\"test note\"}", Instant.now()));
      gate.awaitBlocked();
      commands.sendAndWait(
          new EstablishLinkedApplicationGroupCommand(
              groupId, leadId, List.of(leadId, applicationId), Instant.now()));
      assertThat(gate.observedWhileBlocked()).isFalse();
    } finally {
      gate.release();
    }

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(applications.findById(applicationId).orElseThrow().getLinkedGroupId())
                    .isEqualTo(groupId));
  }

  @Test
  void givenG1ToG2MembershipEventsRace_whenProcessed_thenApplicationReferencesG2()
      throws InterruptedException {
    UUID applicationId = new UUID(0, 5);
    UUID firstLead = UUID.randomUUID();
    UUID secondLead = UUID.randomUUID();
    UUID firstGroup = new UUID(0, 6);
    UUID secondGroup = new UUID(0, 7);
    createApplication(applicationId);
    createApplication(firstLead);
    createApplication(secondLead);

    gate.arm(
        LinkedApplicationGroupCreatedEvent.class,
        firstGroup,
        LinkedApplicationGroupCreatedEvent.class,
        secondGroup);
    try {
      commands.sendAndWait(
          new EstablishLinkedApplicationGroupCommand(
              firstGroup, firstLead, List.of(firstLead, applicationId), Instant.now()));
      gate.awaitBlocked();
      commands.sendAndWait(
          new RemoveApplicationFromLinkedGroupCommand(firstGroup, applicationId, 0, Instant.now()));
      commands.sendAndWait(
          new EstablishLinkedApplicationGroupCommand(
              secondGroup, secondLead, List.of(secondLead, applicationId), Instant.now()));
      assertThat(gate.observedWhileBlocked()).isFalse();
    } finally {
      gate.release();
    }

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(applications.findById(applicationId).orElseThrow().getLinkedGroupId())
                    .isEqualTo(secondGroup));
  }

  @Test
  void givenIndexCreationAndGroupEventsRace_whenProcessed_thenIndexRetainsLead()
      throws InterruptedException {
    UUID applicationId = new UUID(0, 11);
    UUID leadId = UUID.randomUUID();
    UUID groupId = new UUID(0, 12);
    createApplication(leadId);

    gate.arm(
        ApplicationListIndexProjection.class,
        ApplicationCreatedEvent.class,
        applicationId,
        LinkedApplicationGroupCreatedEvent.class,
        groupId);
    try {
      postApplication(applicationId);
      gate.awaitBlocked();
      commands.sendAndWait(
          new EstablishLinkedApplicationGroupCommand(
              groupId, leadId, List.of(leadId, applicationId), Instant.now()));
      assertThat(gate.observedWhileBlocked()).isFalse();
    } finally {
      gate.release();
    }

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(listIndex.findById(applicationId).orElseThrow().getLeadApplicationId())
                    .isEqualTo(leadId));
  }

  @Test
  void givenIndexG1ToG2EventsRace_whenProcessed_thenIndexReferencesG2()
      throws InterruptedException {
    UUID applicationId = new UUID(0, 13);
    UUID firstLead = UUID.randomUUID();
    UUID secondLead = UUID.randomUUID();
    UUID firstGroup = new UUID(0, 14);
    UUID secondGroup = new UUID(0, 15);
    createApplication(applicationId);
    createApplication(firstLead);
    createApplication(secondLead);
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(listIndex.findById(applicationId)).isPresent());

    gate.arm(
        ApplicationListIndexProjection.class,
        LinkedApplicationGroupCreatedEvent.class,
        firstGroup,
        LinkedApplicationGroupCreatedEvent.class,
        secondGroup);
    try {
      commands.sendAndWait(
          new EstablishLinkedApplicationGroupCommand(
              firstGroup, firstLead, List.of(firstLead, applicationId), Instant.now()));
      gate.awaitBlocked();
      commands.sendAndWait(
          new RemoveApplicationFromLinkedGroupCommand(firstGroup, applicationId, 0, Instant.now()));
      commands.sendAndWait(
          new EstablishLinkedApplicationGroupCommand(
              secondGroup, secondLead, List.of(secondLead, applicationId), Instant.now()));
      assertThat(gate.observedWhileBlocked()).isFalse();
    } finally {
      gate.release();
    }

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(listIndex.findById(applicationId).orElseThrow().getLeadApplicationId())
                    .isEqualTo(secondLead));
  }

  private void createApplication(UUID applicationId) {
    postApplication(applicationId);
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(applications.findById(applicationId)).isPresent());
  }

  private void postApplication(UUID applicationId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    assertThat(
            restTemplate
                .postForEntity(
                    "/api/v0/applications",
                    new HttpEntity<>(
                        validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers),
                    Void.class)
                .getStatusCode())
        .isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
  }

  @TestConfiguration
  static class GateConfig {
    @Bean
    ProjectionGate projectionGate() {
      return new ProjectionGate();
    }
  }

  @Aspect
  static class ProjectionGate {
    private volatile Class<?> projectionType = ApplicationProjection.class;
    private volatile Class<?> blockedType;
    private volatile UUID blockedId;
    private volatile Class<?> observedType;
    private volatile UUID observedId;
    private CountDownLatch blocked = new CountDownLatch(1);
    private CountDownLatch released = new CountDownLatch(1);
    private CountDownLatch observed = new CountDownLatch(1);

    void arm(Class<?> blockedType, UUID blockedId, Class<?> observedType, UUID observedId) {
      arm(ApplicationProjection.class, blockedType, blockedId, observedType, observedId);
    }

    void arm(
        Class<?> projectionType,
        Class<?> blockedType,
        UUID blockedId,
        Class<?> observedType,
        UUID observedId) {
      this.projectionType = projectionType;
      this.blockedType = blockedType;
      this.blockedId = blockedId;
      this.observedType = observedType;
      this.observedId = observedId;
      blocked = new CountDownLatch(1);
      released = new CountDownLatch(1);
      observed = new CountDownLatch(1);
    }

    @Around(
        "execution(* uk.gov.justice.laa.dstew.access.query.application.ApplicationProjection.on(..))"
            + " || execution(* uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexProjection.on(..))")
    Object intercept(ProceedingJoinPoint invocation) throws Throwable {
      if (!projectionType.isInstance(invocation.getTarget())) {
        return invocation.proceed();
      }
      Object event = invocation.getArgs()[0];
      UUID eventId =
          switch (event) {
            case ApplicationCreatedEvent created -> created.applicationId();
            case NoteCreatedEvent note -> note.applicationId();
            case LinkedApplicationGroupCreatedEvent group -> group.groupId();
            default -> null;
          };
      if (event.getClass() == observedType && eventId.equals(observedId)) {
        observed.countDown();
      }
      if (event.getClass() == blockedType && eventId.equals(blockedId)) {
        blocked.countDown();
        if (!released.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to release application projection");
        }
      }
      return invocation.proceed();
    }

    void awaitBlocked() throws InterruptedException {
      assertThat(blocked.await(10, TimeUnit.SECONDS)).isTrue();
    }

    boolean observedWhileBlocked() throws InterruptedException {
      return observed.await(2, TimeUnit.SECONDS);
    }

    void release() {
      released.countDown();
    }
  }
}
