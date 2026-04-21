package uk.gov.justice.laa.dstew.access.specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/** Defines the filtering of Application Summaries. */
@ExcludeFromGeneratedCodeCoverage
public class ApplicationSummarySpecification {

  /** Filters Application Summaries based on different filters. */
  public static Specification<ApplicationEntity> filterBy(
      ApplicationStatus status,
      String reference,
      String firstName,
      String lastName,
      LocalDate clientDateOfBirth,
      UUID userId,
      MatterType matterType,
      Boolean isAutoGranted) {
    return isStatus(status)
        .and(likeLaaReference(reference))
        .and(IndividualFilterSpecification.filterIndividual(firstName, lastName, clientDateOfBirth))
        .and(isCaseworkerId(userId))
        .and(isMatterType(matterType))
        .and(isAutoGranted(isAutoGranted));
  }

  private static Specification<ApplicationEntity> isMatterType(MatterType matterType) {
    if (matterType != null) {
      return (root, query, builder) -> builder.equal(root.get("matterType"), matterType);
    }

    return Specification.unrestricted();
  }

  private static Specification<ApplicationEntity> isStatus(ApplicationStatus status) {
    if (status != null) {
      return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    return Specification.unrestricted();
  }

  private static Specification<ApplicationEntity> likeLaaReference(String reference) {
    if (reference != null && !reference.isBlank()) {
      return (root, query, builder) ->
          builder.like(
              builder.lower(root.get("laaReference")), "%" + reference.toLowerCase() + "%");
    }

    return Specification.unrestricted();
  }

  private static Specification<ApplicationEntity> isCaseworkerId(UUID caseworkerId) {

    if (caseworkerId != null) {
      return (root, query, builder) -> {
        Join<ApplicationEntity, CaseworkerEntity> caseworkerJoin =
            root.join("caseworker", JoinType.INNER);
        return builder.equal(caseworkerJoin.get("id"), caseworkerId);
      };
    }

    return Specification.unrestricted();
  }

  @ExcludeFromGeneratedCodeCoverage
  private class IndividualFilterSpecification {
    public static Specification<ApplicationEntity> filterIndividual(
        String firstName, String lastName, LocalDate dateOfBirth) {
      if (!isPopulated(firstName) && !isPopulated(lastName) && dateOfBirth == null) {
        return Specification.unrestricted();
      }

      return (root, query, builder) -> {
        Join<ApplicationEntity, IndividualEntity> individualsJoin =
            root.join("individuals", JoinType.INNER);

        Predicate predicate = builder.equal(individualsJoin.get("type"), IndividualType.CLIENT);

        if (isPopulated(firstName)) {
          predicate =
              builder.and(
                  predicate,
                  builder.like(
                      builder.lower(individualsJoin.get("firstName")),
                      "%" + firstName.toLowerCase() + "%"));
        }
        if (isPopulated(lastName)) {
          predicate =
              builder.and(
                  predicate,
                  builder.like(
                      builder.lower(individualsJoin.get("lastName")),
                      "%" + lastName.toLowerCase() + "%"));
        }
        if (dateOfBirth != null) {
          predicate =
              builder.and(
                  predicate, builder.equal(individualsJoin.get("dateOfBirth"), dateOfBirth));
        }

        return predicate;
      };
    }
  }

  private static boolean isPopulated(String str) {
    return str != null && !str.isBlank();
  }

  private static Specification<ApplicationEntity> isAutoGranted(Boolean isAutoGranted) {
    if (isAutoGranted != null) {
      return (root, query, builder) -> builder.equal(root.get("isAutoGranted"), isAutoGranted);
    }
    return Specification.unrestricted();
  }
}
