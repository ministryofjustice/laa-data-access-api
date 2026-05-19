package uk.gov.justice.laa.dstew.access.model;

import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** DTO for individual data projected as part of an application summary query. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndividualSummaryDto {

  /** The unique identifier of the individual. */
  private UUID id;

  /** The individual's first name. */
  private String firstName;

  /** The individual's last name. */
  private String lastName;

  /** The individual's date of birth. */
  private LocalDate dateOfBirth;

  /** The role this individual plays on the application (e.g. {@link IndividualType#CLIENT}). */
  private IndividualType type;
}
