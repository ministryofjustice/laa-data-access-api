package uk.gov.justice.laa.dstew.access.spike;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.spike.dynamo.DomainEventDynamoDB;

@RequiredArgsConstructor
@Service
public class ReplayService {

  DynamoDbService dynamoDbService;
  S3UploadService s3UploadService;
  public ApplicationEntity getReplayApplication(UUID applicationId, Instant replayEndDateTime) {
    List<DomainEventDynamoDB> allApplicationsById = dynamoDbService.getAllApplicationsByIdUntilTime(applicationId.toString(), replayEndDateTime);
    return null;

  }
}
