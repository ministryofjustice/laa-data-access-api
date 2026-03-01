package uk.gov.justice.laa.dstew.access.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.mapper.DomainEventMapper;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.service.DynamoDbService;
import uk.gov.justice.laa.dstew.access.service.EventHistoryService;
import uk.gov.justice.laa.dstew.access.service.impl.EventHistoryServiceAwsImpl;
import uk.gov.justice.laa.dstew.access.service.impl.EventHistoryServiceRdsImpl;

/**
 * Configuration class for EventHistoryService. It conditionally creates beans based on the "event.history.service.type".
 * If the type is "aws", it creates an instance of EventHistoryServiceAwsImpl.
 * If the type is "rds" or if the property is missing, it creates an instance of EventHistoryServiceRdsImpl.
 */
@Configuration
public class EventHistoryServiceConfig {

  @Bean
  @ConditionalOnProperty(name = "event.history.service.type", havingValue = "aws")
  public EventHistoryService eventHistoryServiceAwsImpl(DynamoDbService dynamoDbService) {
    return new EventHistoryServiceAwsImpl(dynamoDbService);
  }

  @Bean
  @ConditionalOnProperty(name = "event.history.service.type", havingValue = "rds", matchIfMissing = true)
  public EventHistoryService eventHistoryServiceRdsImpl(DomainEventRepository domainEventRepository, DomainEventMapper mapper) {
    return new EventHistoryServiceRdsImpl(domainEventRepository, mapper);
  }
}
