package uk.gov.justice.laa.dstew.access.command.synchronousapplication;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Individual state owned by a SynchronousApplication aggregate. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record SynchronousApplicationIndividual(
    UUID individualId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Map<String, Object> individualContent,
    String type) {}

