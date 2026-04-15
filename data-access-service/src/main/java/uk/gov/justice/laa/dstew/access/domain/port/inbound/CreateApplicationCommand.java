package uk.gov.justice.laa.dstew.access.domain.port.inbound;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.domain.model.Individual;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.LinkedApplication;
import uk.gov.justice.laa.dstew.access.model.ParsedAppContentDetails;

/**
 * Command object carrying all pre-validated data needed by the {@link CreateApplicationUseCase}.
 * Constructed by the driving adapter (controller) from the API request.
 */
@Builder
public record CreateApplicationCommand(
    ApplicationStatus status,
    String laaReference,
    Map<String, Object> applicationContent,
    Set<Individual> individuals,
    ParsedAppContentDetails parsedContent,
    List<LinkedApplication> linkedApplications) {}
