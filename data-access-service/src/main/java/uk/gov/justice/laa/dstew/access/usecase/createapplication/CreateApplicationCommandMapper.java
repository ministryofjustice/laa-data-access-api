package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import java.util.List;
import uk.gov.justice.laa.dstew.access.domain.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.domain.Individual;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;

/** Maps an API ApplicationCreateRequest to a CreateApplicationCommand. */
public class CreateApplicationCommandMapper {

  /** Converts an API request to a use-case command. */
  public CreateApplicationCommand toCommand(ApplicationCreateRequest req) {
    return new CreateApplicationCommand(
        toDomainStatus(req.getStatus()),
        req.getLaaReference(),
        req.getApplicationContent(),
        toDomainIndividuals(req.getIndividuals()));
  }

  private ApplicationStatus toDomainStatus(
      uk.gov.justice.laa.dstew.access.model.ApplicationStatus model) {
    if (model == null) {
      return null;
    }
    return ApplicationStatus.valueOf(model.name());
  }

  private List<Individual> toDomainIndividuals(List<IndividualCreateRequest> reqs) {
    if (reqs == null) {
      return List.of();
    }
    return reqs.stream()
        .map(
            r ->
                new Individual(
                    r.getFirstName(),
                    r.getLastName(),
                    r.getDateOfBirth(),
                    r.getDetails(),
                    r.getType() != null ? r.getType().name() : null))
        .toList();
  }
}
