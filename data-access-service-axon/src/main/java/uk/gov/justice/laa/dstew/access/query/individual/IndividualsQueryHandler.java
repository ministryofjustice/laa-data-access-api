package uk.gov.justice.laa.dstew.access.query.individual;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationApplicant;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
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

  /** Returns current individuals after applying application, type, and pagination filters. */
  @QueryHandler
  public FindIndividualsResult handle(FindIndividualsQuery query) {
    List<ApplicationReadModel> applications = findApplications(query.applicationId());
    List<ApplicationDataId> dataIds =
        applications.stream()
            .map(
                application ->
                    new ApplicationDataId(
                        application.getApplicationId(), application.getApplicationDataVersion()))
            .toList();
    Map<ApplicationDataId, ApplicationDataPayload> payloads = applicationDataStore.getAll(dataIds);

    Map<UUID, ApplicationIndividual> uniqueIndividuals = new LinkedHashMap<>();
    payloads.values().stream()
        .map(ApplicationDataPayload::individuals)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(individual -> matchesType(individual, query.individualType()))
        .sorted(Comparator.comparing(ApplicationIndividual::individualId))
        .forEach(
            individual -> uniqueIndividuals.putIfAbsent(individual.individualId(), individual));

    List<ApplicationIndividual> filtered = List.copyOf(uniqueIndividuals.values());
    int fromIndex = Math.min((query.page() - 1) * query.pageSize(), filtered.size());
    int toIndex = Math.min(fromIndex + query.pageSize(), filtered.size());
    ApplicationClientDetails clientDetails =
        query.includeClientDetails() && "CLIENT".equals(query.individualType())
            ? clientDetails(payloads.values())
            : null;
    return new FindIndividualsResult(
        filtered.subList(fromIndex, toIndex),
        query.page(),
        query.pageSize(),
        filtered.size(),
        clientDetails);
  }

  private List<ApplicationReadModel> findApplications(UUID applicationId) {
    return applicationId == null
        ? applicationRepository.findAll()
        : applicationRepository.findById(applicationId).stream().toList();
  }

  private boolean matchesType(ApplicationIndividual individual, String individualType) {
    return individualType == null || Objects.equals(individual.type(), individualType);
  }

  private ApplicationClientDetails clientDetails(Collection<ApplicationDataPayload> payloads) {
    ApplicationContent content =
        payloads.stream().findFirst().map(ApplicationDataPayload::applicationContent).orElse(null);
    if (content == null) {
      return new ApplicationClientDetails(null, null, null, null, null, null);
    }
    ApplicationApplicant applicant = content.getApplicant();
    return new ApplicationClientDetails(
        content.getLastNameAtBirth(),
        content.getPreviousApplicationId(),
        applicant == null ? null : applicant.getRelationshipToInvolvedChildren(),
        content.getCorrespondenceAddressType(),
        applicant == null ? null : applicant.getAppliedPreviously(),
        applicant == null ? null : applicant.getAddresses());
  }
}
