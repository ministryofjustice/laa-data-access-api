package uk.gov.justice.laa.dstew.access.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryMessage;

/**
 * Sends messages to a queue.
 */
@Service
public class SqsProducer {

  private final SqsAsyncClient sqsAsyncClient;
  private final ObjectMapper objectMapper;
  private final String queueUrl = "http://localhost:4566/000000000000/test-queue";

  /**
   * Create a message producer.
   *
   * @param sqsAsyncClient the client to send messages to.
   * @param objectMapper the JSON mapper to serialize messages with.
   */
  public SqsProducer(SqsAsyncClient sqsAsyncClient, ObjectMapper objectMapper) {
    this.sqsAsyncClient = sqsAsyncClient;
    this.objectMapper = objectMapper;
  }

  /**
   * Send an ApplicationHistoryMessage to the queue.
   *
   * @param message the object to serialize and send.
   */
  public void createHistoricRecord(ApplicationHistoryMessage message) {
    try {
      String json = objectMapper.writeValueAsString(message);
      SendMessageRequest request = SendMessageRequest.builder()
          .queueUrl(queueUrl)
          .messageBody(json)
          .build();

      CompletableFuture<?> future = sqsAsyncClient.sendMessage(request);
      future.thenAccept(result -> System.out.println("Message sent! ID: " + result));
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize message", e);
    }
  }
}
