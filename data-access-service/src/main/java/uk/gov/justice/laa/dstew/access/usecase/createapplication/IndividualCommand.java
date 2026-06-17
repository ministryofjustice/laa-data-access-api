package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import java.time.LocalDate;
import java.util.Map;
import lombok.Builder;

/** Command record carrying individual fields for the createApplication use case. */
@Builder(toBuilder = true)
public record IndividualCommand(
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Map<String, Object> individualContent,
    String type) {}
