package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;

/** Proves the public Axon submission routes against PostgreSQL and a real LocalStack SNS queue. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationSubmittedLocalStackIntegrationTest {

  private static final GenericContainer<?> LOCALSTACK =
      new GenericContainer<>(DockerImageName.parse("localstack/localstack:4.14.0"))
          .withEnv("SERVICES", "sns,sqs")
          .withExposedPorts(4566)
          .waitingFor(Wait.forHttp("/_localstack/health").forPort(4566));

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  private static final SnsClient SNS_CLIENT;
  private static final SqsClient SQS_CLIENT;
  private static final String TOPIC_ARN;
  private static final String QUEUE_URL;

  static {
    LOCALSTACK.start();
    var credentials =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create("local-access-key", "local-secret-key"));
    URI endpoint = localStackEndpoint();
    SNS_CLIENT =
        SnsClient.builder()
            .endpointOverride(endpoint)
            .region(Region.EU_WEST_2)
            .credentialsProvider(credentials)
            .build();
    SQS_CLIENT =
        SqsClient.builder()
            .endpointOverride(endpoint)
            .region(Region.EU_WEST_2)
            .credentialsProvider(credentials)
            .build();
    TOPIC_ARN = SNS_CLIENT.createTopic(request -> request.name("data-access-events")).topicArn();
    QUEUE_URL =
        SQS_CLIENT
            .createQueue(request -> request.queueName("application-submitted-queue"))
            .queueUrl();
    String queueArn =
        SQS_CLIENT
            .getQueueAttributes(
                GetQueueAttributesRequest.builder()
                    .queueUrl(QUEUE_URL)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build())
            .attributes()
            .get(QueueAttributeName.QUEUE_ARN);
    subscribeQueue(queueArn);
    System.setProperty("aws.accessKeyId", "local-access-key");
    System.setProperty("aws.secretAccessKey", "local-secret-key");
  }

  @DynamicPropertySource
  static void awsProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.cloud.aws.endpoint", () -> localStackEndpoint().toString());
    registry.add("spring.cloud.aws.region.static", () -> "eu-west-2");
    registry.add("application.integration-events.topic-arn", () -> TOPIC_ARN);
  }

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearQueue() {
    while (!receiveOnce().isEmpty()) {
      // Drain messages left by the preceding scenario without relying on purge timing semantics.
    }
  }

  @AfterAll
  static void closeAwsClients() {
    SNS_CLIENT.close();
    SQS_CLIENT.close();
    LOCALSTACK.stop();
    System.clearProperty("aws.accessKeyId");
    System.clearProperty("aws.secretAccessKey");
  }

  @Test
  void givenSubmittedCreate_whenTransactionCommits_thenQueueReceivesThinEvent() {
    UUID applicationId = UUID.randomUUID();
    HttpHeaders headers = headers("corr-create-submitted");

    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            applicationUrl(),
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers),
            Void.class);

    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    assertThinEvent(receiveEvent(applicationId), applicationId, 0L, "corr-create-submitted");
  }

  @Test
  void givenInProgressCreate_whenUpdatedToSubmitted_thenOnlyUpdatePublishes() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest submitted =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    ApplicationCreateRequest inProgress =
        ApplicationCreateRequest.builder()
            .applicationType(submitted.getApplicationType())
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .applicationContent(submitted.getApplicationContent())
            .laaReference(submitted.getLaaReference())
            .individuals(submitted.getIndividuals())
            .build();
    ResponseEntity<Void> createResponse =
        restTemplate.postForEntity(
            applicationUrl(),
            new HttpEntity<>(inProgress, headers("corr-create-in-progress")),
            Void.class);
    assertThat(createResponse.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    assertThat(receiveOnce()).isEmpty();

    ApplicationUpdateRequest update =
        new ApplicationUpdateRequest()
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .applicationContent(submitted.getApplicationContent());
    ResponseEntity<Void> updateResponse =
        restTemplate.exchange(
            applicationUrl() + "/" + applicationId,
            HttpMethod.PATCH,
            new HttpEntity<>(update, headers("corr-update-submitted")),
            Void.class);

    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThinEvent(receiveEvent(applicationId), applicationId, 1L, "corr-update-submitted");
  }

  @Test
  void givenSubmittedAggregate_whenReloadedForAnotherCommand_thenNothingIsRepublished() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest submitted =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    restTemplate.postForEntity(
        applicationUrl(), new HttpEntity<>(submitted, headers("corr-reload-create")), Void.class);
    assertThat(receiveEvent(applicationId)).contains("corr-reload-create");

    ResponseEntity<Void> noteResponse =
        restTemplate.exchange(
            applicationUrl() + "/" + applicationId + "/notes",
            HttpMethod.POST,
            new HttpEntity<>(new CreateNoteRequest("Reload aggregate"), headers("corr-reload")),
            Void.class);

    assertThat(noteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(receiveOnce()).isEmpty();
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void givenSnsUnavailableAfterCommit_whenSubmitted_thenApplicationRemainsCommitted(
      CapturedOutput output) {
    UUID applicationId = UUID.randomUUID();
    SNS_CLIENT.deleteTopic(request -> request.topicArn(TOPIC_ARN));

    ResponseEntity<Void> response;
    try {
      response =
          restTemplate.postForEntity(
              applicationUrl(),
              new HttpEntity<>(
                  validCreateApplicationRequest(applicationId, UUID.randomUUID()),
                  headers("corr-publish-failure")),
              Void.class);
    } finally {
      String recreatedTopicArn =
          SNS_CLIENT.createTopic(request -> request.name("data-access-events")).topicArn();
      assertThat(recreatedTopicArn).isEqualTo(TOPIC_ARN);
      subscribeQueue(queueArn());
    }

    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_data WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
                Integer.class,
                applicationId.toString()))
        .isEqualTo(1);
    assertThat(output)
        .contains(
            "Failed to publish ApplicationSubmitted event after commit",
            applicationId.toString(),
            "corr-publish-failure");
    assertThat(receiveOnce()).isEmpty();
  }

  private static URI localStackEndpoint() {
    return URI.create("http://" + LOCALSTACK.getHost() + ":" + LOCALSTACK.getMappedPort(4566));
  }

  private static String queueArn() {
    return SQS_CLIENT
        .getQueueAttributes(
            GetQueueAttributesRequest.builder()
                .queueUrl(QUEUE_URL)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build())
        .attributes()
        .get(QueueAttributeName.QUEUE_ARN);
  }

  private static void subscribeQueue(String queueArn) {
    SNS_CLIENT.subscribe(request -> request.topicArn(TOPIC_ARN).protocol("sqs").endpoint(queueArn));
  }

  private String applicationUrl() {
    return "http://localhost:" + port + "/api/v0/applications";
  }

  private HttpHeaders headers(String correlationId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    headers.set("X-Correlation-Id", correlationId);
    return headers;
  }

  private String receiveEvent(UUID applicationId) {
    return await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .until(
            () ->
                receiveOnce().stream()
                    .filter(body -> body.contains(applicationId.toString()))
                    .findFirst()
                    .orElse(null),
            java.util.Objects::nonNull);
  }

  private List<String> receiveOnce() {
    return SQS_CLIENT
        .receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .waitTimeSeconds(1)
                .maxNumberOfMessages(10)
                .build())
        .messages()
        .stream()
        .map(
            message -> {
              SQS_CLIENT.deleteMessage(
                  DeleteMessageRequest.builder()
                      .queueUrl(QUEUE_URL)
                      .receiptHandle(message.receiptHandle())
                      .build());
              return message.body();
            })
        .toList();
  }

  private void assertThinEvent(
      String body, UUID applicationId, long applicationVersion, String correlationId) {
    assertThat(body)
        .contains(
            "ApplicationSubmitted",
            applicationId.toString(),
            "\\\"applicationVersion\\\":" + applicationVersion,
            correlationId)
        .doesNotContain(
            "applicationContent", "proceedings", "individuals", "Ada", "Lovelace", "LAA-123");
  }
}
