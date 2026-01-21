package uk.gov.justice.laa.dstew.access.specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/**
 * Defines the filtering of Application Summaries.
 *
 */
@ExcludeFromGeneratedCodeCoverage
public class ApplicationSummarySpecification {

  /**
   * Filters Application Summaries based on different filters.
   *
   */
  public static Specification<ApplicationSummaryEntity> filterBy(
          ApplicationStatus status,
          String reference,
          String firstName,
          String lastName,
          LocalDate clientDateOfBirth,
          UUID userId,
          Boolean isAutoGranted) {
    return isStatus(status)
            .and(likeLaaReference(reference))
            .and(IndividualFilterSpecification.filterIndividual(firstName, lastName, clientDateOfBirth))
            .and(isCaseworkerId(userId))
            .and(isAutoGranted(isAutoGranted));
  }

  private static Specification<ApplicationSummaryEntity> isStatus(ApplicationStatus status) {
    if (status != null) {
      return (root, query, builder)
              -> builder.equal(root.get("status"), status);
    }

    return Specification.unrestricted();
  }

  private static Specification<ApplicationSummaryEntity> likeLaaReference(String reference) {
    if (reference != null && !reference.isBlank()) {
      return (root, query, builder)
              -> builder.like(builder.lower(root.get("laaReference")),
              "%" + reference.toLowerCase() + "%");
    }

    return Specification.unrestricted();
  }

  private static Specification<ApplicationSummaryEntity> isCaseworkerId(UUID caseworkerId) {

    if (caseworkerId != null) {
      return (root, query, builder)
            -> {
              Join<ApplicationEntity, CaseworkerEntity> caseworkerJoin = root.join("caseworker", JoinType.INNER);
              return builder.equal(caseworkerJoin.get("id"), caseworkerId);
      };
    }

    return Specification.unrestricted();
  }

  @ExcludeFromGeneratedCodeCoverage
  private class IndividualFilterSpecification {
    public static Specification<ApplicationSummaryEntity> filterIndividual(String firstName, 
        String lastName, LocalDate dateOfBirth) {
      Specification<ApplicationSummaryEntity> baseSpecification = Specification.unrestricted();
      if (isPopulated(firstName) 
          || isPopulated(lastName) 
          || dateOfBirth != null) {
        baseSpecification = isClient();
      }
      return baseSpecification
        .and(likeFirstName(firstName))
        .and(likeLastName(lastName))
        .and(isDateOfBirth(dateOfBirth));
    }

    private static Specification<ApplicationSummaryEntity> isClient() {
      return (root, query, builder)
                -> {
                  Join<ApplicationEntity, IndividualEntity> individualsJoin = root.join("individuals", JoinType.INNER);
                  return builder.equal(individualsJoin.get("type"), IndividualType.CLIENT);
      };
    }

    private static Specification<ApplicationSummaryEntity> likeFirstName(String firstName) {
      if (firstName != null && !firstName.isBlank()) {
        return (root, query, builder)
                -> {
                  Join<ApplicationEntity, IndividualEntity> individualsJoin = root.join("individuals", JoinType.INNER);
                  return builder.like(builder.lower(
                                  individualsJoin.get("firstName")),
                                    "%" + firstName.toLowerCase() + "%");
        };
      }
      return Specification.unrestricted();
    }

    private static Specification<ApplicationSummaryEntity> likeLastName(String lastName) {
      if (lastName != null && !lastName.isBlank()) {
        return (root, query, builder)
                -> {
                  Join<ApplicationEntity, IndividualEntity> individualsJoin = root.join("individuals", JoinType.INNER);
                  return builder.like(builder.lower(
                                individualsJoin.get("lastName")),
                  "%" + lastName.toLowerCase() + "%");
        };
      }
      return Specification.unrestricted();
    }

    private static Specification<ApplicationSummaryEntity> isDateOfBirth(LocalDate clientDateOfBirth) {
      if (clientDateOfBirth != null) {
        return (root, query, builder)
              -> {
                Join<ApplicationEntity, IndividualEntity> individualsJoin = root.join("individuals", JoinType.INNER);
                return builder.equal(individualsJoin.get("dateOfBirth"), clientDateOfBirth);
        };
      }
      return Specification.unrestricted();
    }
  }

  private static boolean isPopulated(String str) {
    return str != null && !str.isBlank();
  }

  private static Specification<ApplicationSummaryEntity> isAutoGranted(Boolean isAutoGranted) {
    if (isAutoGranted != null) {
      return (root, query, builder)
            -> builder.equal(root.get("isAutoGranted"), isAutoGranted);
      
    }
    return Specification.unrestricted();
  }
}
