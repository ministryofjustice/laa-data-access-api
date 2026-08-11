package uk.gov.justice.laa.dstew.access.query.individual;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;

/** Handles individual searches over the current immutable data version of each application. */
@Component
public class IndividualsQueryHandler {

  private final ApplicationReadRepository applicationRepository;
  private final ApplicationDataStore applicationDataStore;

  public IndividualsQueryHandler(
      ApplicationReadRepository applicationRepository, ApplicationDataStore applicationDataStore) {
    this.applicationRepository = applicationRepository;
    this.applicationDataStore = applicationDataStore;
  }

  /** Returns current client after applying application and type filters. */
  @QueryHandler
  public FindIndividualsResult handle(FindIndividualsQuery query) {
    // If type filter is not CLIENT, return empty
    if (query.individualType() != null && !"CLIENT".equals(query.individualType())) {
      return new FindIndividualsResult(null, query.page(), query.pageSize(), 0, null);
    }

    List<ApplicationReadModel> applications = findApplications(query.applicationId());
    List<ApplicationDataId> dataIds =
        applications.stream()
            .map(
                application ->
                    new ApplicationDataId(
                        application.getApplicationId(), application.getApplicationDataVersion()))
            .toList();
    Map<ApplicationDataId, ApplicationDataPayload> payloads = applicationDataStore.getAll(dataIds);

    ApplicationClient client =
        payloads.values().stream()
            .map(ApplicationDataPayload::client)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    ApplicationClientDetails clientDetails =
        query.includeClientDetails() && client != null
            ? new ApplicationClientDetails(
                client.getLastNameAtBirth(),
                client.getPreviousApplicationId(),
                client.getRelationshipToInvolvedChildren(),
                client.getAppliedPreviously(),
                client.getAddresses())
            : null;

    int totalRecords = client != null ? 1 : 0;
    return new FindIndividualsResult(
        client, query.page(), query.pageSize(), totalRecords, clientDetails);
  }

  private List<ApplicationReadModel> findApplications(UUID applicationId) {
    return applicationId == null
        ? applicationRepository.findAll()
        : applicationRepository.findById(applicationId).stream().toList();
  }
}
