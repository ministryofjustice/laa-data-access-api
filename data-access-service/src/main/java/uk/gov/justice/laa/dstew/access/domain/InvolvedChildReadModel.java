package uk.gov.justice.laa.dstew.access.domain;

import java.time.LocalDate;
import lombok.Builder;

/** Read-model record for a child linked to a proceeding in the get-application response. */
@Builder(toBuilder = true)
public record InvolvedChildReadModel(String fullName, LocalDate dateOfBirth) {}
