package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.Proceeding;

/** Maps command records to domain records within the createApplication use-case layer. */
public class CreateApplicationDomainMapper {

  static final int APPLICATION_SCHEMA_VERSION = 1;

  /**
   * Builds an {@link ApplicationDomain} from a {@link CreateApplicationCommand} and the pre-parsedDetails
   * {@link ParsedAppContentDetails}. The resulting domain is pre-save: {@code id} and {@code
   * createdAt} are null.
   *
   * @param command the command carrying all fields from the HTTP request
   * @param parsedDetails the details extracted from the application content
   * @return a new {@link ApplicationDomain} ready for persistence
   */
  public ApplicationDomain toApplicationDomain(
      CreateApplicationCommand command, ParsedAppContentDetails parsedDetails) {
    return ApplicationDomain.builder()
        .status(command.status())
        .laaReference(command.laaReference())
        .applicationContent(command.applicationContent())
        .individuals(toIndividualDomains(command.individuals()))
        .schemaVersion(APPLICATION_SCHEMA_VERSION)
        .applyApplicationId(parsedDetails.applyApplicationId())
        .usedDelegatedFunctions(parsedDetails.usedDelegatedFunctions())
        .categoryOfLaw(parsedDetails.categoryOfLaw() != null ? parsedDetails.categoryOfLaw().name() : null)
        .matterType(parsedDetails.matterType() != null ? parsedDetails.matterType().name() : null)
        .submittedAt(parsedDetails.submittedAt())
        .officeCode(parsedDetails.officeCode())
        .proceedings(toProceedingDomains(parsedDetails.proceedings()))
        .build();
  }

  /**
   * Converts a list of {@link IndividualCommand} records into a set of {@link IndividualDomain}.
   *
   * @param individuals the list of individual individuals
   * @return a set of individual domain records
   */
  public Set<IndividualDomain> toIndividualDomains(List<IndividualCommand> individuals) {
    if (individuals == null) {
      return Set.of();
    }
    return individuals.stream().map(this::toIndividualDomain).collect(Collectors.toSet());
  }

  /**
   * Converts a single {@link IndividualCommand} to an {@link IndividualDomain}.
   *
   * @param individual the individual individual
   * @return the individual domain record
   */
  public IndividualDomain toIndividualDomain(IndividualCommand individual) {
    return IndividualDomain.builder()
        .firstName(individual.firstName())
        .lastName(individual.lastName())
        .dateOfBirth(individual.dateOfBirth())
        .individualContent(individual.individualContent())
        .type(individual.type())
        .build();
  }

  public Set<ProceedingDomain> toProceedingDomains(List<Proceeding> proceedings) {
    if (proceedings == null) {
      return new LinkedHashSet<>();
    }
    return proceedings.stream()
        .filter(Objects::nonNull)
        .map(this::toProceedingDomain)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public ProceedingDomain toProceedingDomain(Proceeding proceeding) {
    return ProceedingDomain.builder()
        .applyProceedingId(proceeding.getId())
        .isLead(Boolean.TRUE.equals(proceeding.getLeadProceeding()))
        .description(proceeding.getDescription())
        .proceedingContent(toProceedingContentMap(proceeding))
        .createdBy("")
        .updatedBy("")
        .build();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> toProceedingContentMap(Proceeding proceeding) {
    return MapperUtil.getObjectMapper().convertValue(proceeding, Map.class);
  }
}
