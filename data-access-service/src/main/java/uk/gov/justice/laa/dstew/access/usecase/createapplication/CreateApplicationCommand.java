package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import java.util.List;
import java.util.Map;
import uk.gov.justice.laa.dstew.access.domain.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.domain.Individual;

/** Command object for creating an application. */
public record CreateApplicationCommand(
    ApplicationStatus status,
    String laaReference,
    Map<String, Object> applicationContent,
    List<Individual> individuals) {}
