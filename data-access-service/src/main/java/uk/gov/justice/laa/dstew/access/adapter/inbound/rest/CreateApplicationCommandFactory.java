package uk.gov.justice.laa.dstew.access.adapter.inbound.rest;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.domain.model.Individual;
import uk.gov.justice.laa.dstew.access.domain.port.inbound.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.service.ApplicationContentParserService;
import uk.gov.justice.laa.dstew.access.validation.PayloadValidationService;

/**
 * Converts an API-layer {@link ApplicationCreateRequest} into a domain {@link
 * CreateApplicationCommand}. Performs payload validation and content parsing as part of the
 * conversion — these are driving-adapter concerns that do not belong in the controller or the
 * domain.
 */
@Component
@RequiredArgsConstructor
public class CreateApplicationCommandFactory {

  private final PayloadValidationService payloadValidationService;
  private final ApplicationContentParserService applicationContentParserService;

  /**
   * Validates the raw request payload, parses application content, and builds a {@link
   * CreateApplicationCommand} ready for the use case.
   *
   * @param req the API create-application request
   * @return a fully populated command
   */
  public CreateApplicationCommand toCommand(ApplicationCreateRequest req) {
    ApplicationContent applicationContent =
        payloadValidationService.convertAndValidate(
            req.getApplicationContent(), ApplicationContent.class);

    var parsedContent =
        applicationContentParserService.normaliseApplicationContentDetails(applicationContent);

    Set<Individual> individuals =
        req.getIndividuals() == null
            ? Set.of()
            : req.getIndividuals().stream()
                .map(CreateApplicationCommandFactory::toIndividual)
                .collect(Collectors.toSet());

    return CreateApplicationCommand.builder()
        .status(req.getStatus())
        .laaReference(req.getLaaReference())
        .applicationContent(req.getApplicationContent())
        .individuals(individuals)
        .parsedContent(parsedContent)
        .linkedApplications(applicationContent.getAllLinkedApplications())
        .build();
  }

  private static Individual toIndividual(IndividualCreateRequest req) {
    return Individual.builder()
        .firstName(req.getFirstName())
        .lastName(req.getLastName())
        .dateOfBirth(req.getDateOfBirth())
        .individualContent(req.getDetails())
        .type(req.getType())
        .build();
  }
}
