package uk.gov.justice.laa.dstew.access.integrationevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.ObjectMapper;

class SnsApplicationSubmittedPublisherTest {

  @Test
  void givenVersionOneEvent_whenPublished_thenSendsThinBodyAndFilterAttributes() throws Exception {
    SnsClient snsClient = mock(SnsClient.class);
    ObjectMapper objectMapper = new ObjectMapper();
    SnsApplicationSubmittedPublisher publisher =
        new SnsApplicationSubmittedPublisher(
            snsClient, objectMapper, "arn:aws:sns:eu-west-2:000000000000:data-access-events");
    UUID applicationId = UUID.fromString("8c9e6c2e-4f1a-4e3a-9c2b-1a2b3c4d5e6f");
    ApplicationSubmittedEvent event =
        new ApplicationSubmittedEvent(
            "ApplicationSubmitted",
            1,
            UUID.fromString("b6e6f0b2-0000-4000-8000-000000000001"),
            Instant.parse("2026-08-03T09:30:00Z"),
            "laa-data-access-api",
            "corr-2096",
            new ApplicationSubmittedData(applicationId, applicationId, "LAA-2096", 3L));

    publisher.publish(event, "APPLY");

    ArgumentCaptor<PublishRequest> request = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(request.capture());
    assertThat(request.getValue().topicArn())
        .isEqualTo("arn:aws:sns:eu-west-2:000000000000:data-access-events");
    assertThat(request.getValue().messageAttributes().get("eventType").stringValue())
        .isEqualTo("ApplicationSubmitted");
    assertThat(request.getValue().messageAttributes().get("applicationType").stringValue())
        .isEqualTo("APPLY");

    var body = objectMapper.readTree(request.getValue().message());
    assertThat(body.get("eventType").asText()).isEqualTo("ApplicationSubmitted");
    assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(body.get("data").get("applicationId").asText()).isEqualTo(applicationId.toString());
    assertThat(body.get("data").get("applicationVersion").asLong()).isEqualTo(3L);
    assertThat(body.has("applicationContent")).isFalse();
    assertThat(body.get("data").has("proceedings")).isFalse();
    assertThat(body.get("data").has("individuals")).isFalse();
    assertThat(body.get("data").has("provider")).isFalse();
  }
}
