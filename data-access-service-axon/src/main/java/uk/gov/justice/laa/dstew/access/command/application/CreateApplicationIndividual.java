package uk.gov.justice.laa.dstew.access.command.application;

import java.time.LocalDate;
import java.util.Map;

/** Individual data carried by a create-application command. */
public record CreateApplicationIndividual(
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Map<String, Object> individualContent,
    String type) {}
