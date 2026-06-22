package uk.gov.justice.laa.dstew.access.domain;

import java.time.LocalDate;
import lombok.Builder;

/** Domain record for a child linked to a proceeding in the read model. */
@Builder(toBuilder = true)
public record InvolvedChildDomain(String fullName, LocalDate dateOfBirth) {}
