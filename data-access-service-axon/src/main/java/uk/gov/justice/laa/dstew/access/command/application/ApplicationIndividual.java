package uk.gov.justice.laa.dstew.access.command.application;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Individual state owned by an Application aggregate. */
public record ApplicationIndividual(
    UUID individualId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Map<String, Object> individualContent,
    String type) {}
