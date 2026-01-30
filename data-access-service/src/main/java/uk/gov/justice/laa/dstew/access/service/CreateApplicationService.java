package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.RequestApplicationContent;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.spike.DynamoDbService;
import uk.gov.justice.laa.dstew.access.spike.Event;
import uk.gov.justice.laa.dstew.access.spike.EventType;
import uk.gov.justice.laa.dstew.access.spike.S3UploadResult;
import uk.gov.justice.laa.dstew.access.spike.S3UploadService;

@Slf4j
@RequiredArgsConstructor
@Service
public class CreateApplicationService {
  private final int applicationVersion = 1;

  private final ProceedingsService proceedingsService;
  private final ObjectMapper objectMapper;
  private final ApplicationMapper applicationMapper;
  private final ApplicationRepository applicationRepository;
  private final DomainEventService domainEventService;
  private final S3UploadService s3UploadService;
  private final DynamoDbService dynamoDbService;
  private final ApplicationContentParserService applicationContentParser;

  /**
   * Create a new application.
   *
   * @param req DTO containing creation fields
   * @return UUID of the created application
   */
  public UUID createApplication(final ApplicationCreateRequest req) {
    final ApplicationEntity saved = createApplicationTransactional(req);
    createAndSaveEventDetails(saved, saved.getId());

    return saved.getId();
  }

  @Transactional
  @NonNull
  protected ApplicationEntity createApplicationTransactional(ApplicationCreateRequest req) {
    ApplicationEntity entity = applicationMapper.toApplicationEntity(req);
    RequestApplicationContent requestApplicationContent =
        objectMapper.convertValue(req.getApplicationContent(), RequestApplicationContent.class);
    setValuesFromApplicationContent(entity, requestApplicationContent);
    entity.setSchemaVersion(applicationVersion);

    final ApplicationEntity saved = applicationRepository.save(entity);


    proceedingsService.saveProceedings(requestApplicationContent.getApplicationContent(), saved.getId());
    domainEventService.saveCreateApplicationDomainEvent(saved, null);
    return saved;
  }

  /**
   * Sets key fields in the application entity based on parsed application content.
   *
   * @param entity application entity to update
   * @param requestAppContent application content from the request
   */
  private void setValuesFromApplicationContent(ApplicationEntity entity,
                                               RequestApplicationContent requestAppContent) {


    var parsedContentDetails = applicationContentParser.normaliseApplicationContentDetails(requestAppContent);
    entity.setApplyApplicationId(parsedContentDetails.applyApplicationId());
    entity.setUseDelegatedFunctions(parsedContentDetails.usedDelegatedFunctions());
    entity.setCategoryOfLaw(parsedContentDetails.categoryOfLaw());
    entity.setMatterType(parsedContentDetails.matterType());
    entity.setSubmittedAt(parsedContentDetails.submittedAt());
  }

  /**
   * Placeholder for historic/audit record creation.
   *
   * @param entity application entity
   * @param id
   */
  protected void createAndSaveEventDetails(final ApplicationEntity entity, UUID id) {


    // Implement audit/history publishing if required
    S3UploadResult s3UploadResult = s3UploadService.upload(entity, "laa-data-stewardship-access-bucket", "application-" + id);
    Event event = Event.builder()
        .eventType(EventType.CREATE_APPLICATION)
        .eventId(entity.getApplyApplicationId().toString())
        .caseworkerId(entity.getLaaReference())
        .build();
    CompletableFuture<Event> eventCompletableFuture = dynamoDbService.saveDomainEvent(event, s3UploadResult.getS3Url());
    eventCompletableFuture.whenComplete((event1, throwable) -> {
      if (throwable != null) {
        log.error("Failed to save event: " + throwable.getMessage());
      } else {
        log.info("Event saved successfully: " + event1);

      }
    });
  }

}
