package uk.gov.justice.laa.dstew.access.query.individual;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationCurrentStateAccessPolicy;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadQueryGateway;
import uk.gov.justice.laa.dstew.access.query.utils.security.ReadAccessPolicy;

/** Handles individual searches over the current immutable data version of each application. */
@Component
public class IndividualsQueryHandler {

  private final ApplicationReadQueryGateway applicationReadQueryGateway;
  private final ApplicationDataStore applicationDataStore;
  private final ReadAccessPolicy<ApplicationReadModel> accessPolicy;

  /**
   * Creates the IndividualsQueryHandler.
   *
   * @param applicationReadQueryGateway - gateway for application read model infrastructure
   * @param applicationDataStore - gateway for application data store infrastructure
   * @param accessPolicy - access policies for applications
   */
  public IndividualsQueryHandler(
      ApplicationReadQueryGateway applicationReadQueryGateway,
      ApplicationDataStore applicationDataStore,
      ApplicationCurrentStateAccessPolicy accessPolicy) {
    this.applicationReadQueryGateway = applicationReadQueryGateway;
    this.applicationDataStore = applicationDataStore;
    this.accessPolicy = accessPolicy;
  }

  /** Returns current client after applying application and type filters. */
  @QueryHandler
  public FindIndividualsResult handle(FindIndividualsQuery query) {
    // If type filter is not CLIENT, return empty
    if (query.individualType() != null && !"CLIENT".equals(query.individualType())) {
      return new FindIndividualsResult(null, query.page(), query.pageSize(), 0, false);
    }

    List<ApplicationReadModel> applications = findApplications(query);
    if (applications.isEmpty()) {
      return new FindIndividualsResult(null, query.page(), query.pageSize(), 0, false);
    }
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

    int totalRecords = client != null ? 1 : 0;
    return new FindIndividualsResult(
        client, query.page(), query.pageSize(), totalRecords, query.includeClientDetails());
  }

  private List<ApplicationReadModel> findApplications(FindIndividualsQuery query) {
    var access = accessPolicy.restrictionFor(query.accessScope());
    if (query.applicationId() == null) {
      return applicationReadQueryGateway.findAllApplications(
          (root, criteriaQuery, cb) -> cb.conjunction(), access);
    }
    return applicationReadQueryGateway
        .findApplication(
            (root, criteriaQuery, cb) -> cb.equal(root.get("applicationId"), query.applicationId()),
            access)
        .stream()
        .toList();
  }
}
