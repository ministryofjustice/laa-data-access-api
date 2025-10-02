// package uk.gov.justice.laa.dstew.access.config;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import io.awspring.cloud.sqs.annotation.SqsListener;
// import lombok.RequiredArgsConstructor;
// import org.springframework.stereotype.Component;
// import uk.gov.justice.laa.dstew.access.service.ApplicationService;

// /**
//  * Consumes messages from a queue.
//  */
// @Component
// @RequiredArgsConstructor
// public class SqsConsumer {

//   private final ObjectMapper objectMapper;
//   private final ApplicationService applicationService;

//   /**
//    * Message listener for the SQS queue.
//    *
//    * @param message the message being received.
//    */
//   @SqsListener("test-queue")
//   public void receiveMessage(String message) {
//   }
// }
