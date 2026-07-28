package uk.gov.justice.laa.dstew.access.command.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Individual state owned by a Application aggregate. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record ApplicationIndividual(
    UUID individualId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Map<String, Object> individualContent,
    String type) {}
