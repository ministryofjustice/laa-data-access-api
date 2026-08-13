package uk.gov.justice.laa.dstew.access.integrationevent;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** AWS SNS adapter for {@link ApplicationSubmittedEvent}. */
public class SnsApplicationSubmittedPublisher implements ApplicationSubmittedPublisher {

  private final SnsClient snsClient;
  private final ObjectMapper objectMapper;
  private final String topicArn;

  /** Creates an SNS publisher targeting the configured Data Access integration-events topic. */
  public SnsApplicationSubmittedPublisher(
      SnsClient snsClient, ObjectMapper objectMapper, String topicArn) {
    this.snsClient = snsClient;
    this.objectMapper = objectMapper;
    this.topicArn = topicArn;
  }

  @Override
  public void publish(ApplicationSubmittedEvent event, String applicationType) {
    try {
      snsClient.publish(
          PublishRequest.builder()
              .topicArn(topicArn)
              .message(objectMapper.writeValueAsString(event))
              .messageAttributes(
                  java.util.Map.of(
                      "eventType", stringAttribute(event.eventType()),
                      "applicationType", stringAttribute(applicationType)))
              .build());
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise ApplicationSubmitted event", exception);
    }
  }

  private MessageAttributeValue stringAttribute(String value) {
    return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
  }
}
