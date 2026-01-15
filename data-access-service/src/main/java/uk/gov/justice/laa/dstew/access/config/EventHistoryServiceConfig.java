package uk.gov.justice.laa.dstew.access.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.service.EventHistoryService;
import uk.gov.justice.laa.dstew.access.service.DynamoDbService;
import uk.gov.justice.laa.dstew.access.service.impl.EventHistoryServiceAwsImpl;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.mapper.DomainEventMapper;
import uk.gov.justice.laa.dstew.access.service.impl.EventHistoryServiceRdsImpl;

@Configuration
public class EventHistoryServiceConfig {

    @Bean
    @ConditionalOnProperty(name = "event.history.service.type", havingValue = "aws", matchIfMissing = true)
    public EventHistoryService eventHistoryServiceAwsImpl(DynamoDbService dynamoDbService) {
        return new EventHistoryServiceAwsImpl(dynamoDbService);
    }

    @Bean
    @ConditionalOnProperty(name = "event.history.service.type", havingValue = "rds")
    public EventHistoryService eventHistoryServiceRdsImpl(DomainEventRepository domainEventRepository, DomainEventMapper mapper) {
        return new EventHistoryServiceRdsImpl(domainEventRepository, mapper);
    }
}
