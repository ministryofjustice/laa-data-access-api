package uk.gov.justice.laa.dstew.access.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

/**
 * Validates DynamoDB connectivity at startup so misconfiguration (wrong endpoint, region, or table name)
 * is obvious during local development.
 */
@Component
public class DynamoDbStartupValidator {

  private static final Logger log = LoggerFactory.getLogger(DynamoDbStartupValidator.class);

  private final DynamoDbClient dynamoDbClient;

  @Value("${aws.dynamodb.table-name:EventIndexTable}")
  private String tableName;

  @Value("${aws.endpoint:}")
  private String endpoint;

  @Value("${aws.region:eu-west-2}")
  private String region;

  public DynamoDbStartupValidator(DynamoDbClient dynamoDbClient) {
    this.dynamoDbClient = dynamoDbClient;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void validate() {
    log.info("DynamoDB config: region={}, endpoint='{}', tableName='{}'", region, endpoint, tableName);

    try {
      dynamoDbClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
      log.info("DynamoDB table '{}' exists and is reachable.", tableName);
    } catch (ResourceNotFoundException rnfe) {
      log.error(
          "DynamoDB table '{}' not found. If running locally, create it in LocalStack " +
              "(e.g. `make init-local-resources`) and ensure aws.endpoint points to http://localhost:4566.",
          tableName);
      throw rnfe;
    }
  }
}

