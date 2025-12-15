package uk.gov.justice.laa.dstew.access.specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

/**
 * Defines the filtering of Application Summaries.
 *
 */
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
          UUID userId) {
    return isStatus(status)
            .and(likeApplicationReference(reference))
            .and(likeFirstName(firstName))
            .and(likeLastName(lastName))
            .and(isCaseworkerId(userId));
  }

  private static Specification<ApplicationSummaryEntity> isStatus(ApplicationStatus status) {
    if (status != null) {
      return (root, query, builder)
              -> builder.equal(root.get("status"), status);
    }

    return Specification.unrestricted();
  }

  private static Specification<ApplicationSummaryEntity> likeApplicationReference(String reference) {
    if (reference != null && !reference.isBlank()) {
      return (root, query, builder)
              -> builder.like(builder.lower(root.get("laaReference")),
              "%" + reference.toLowerCase() + "%");
    }

    return Specification.unrestricted();
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
}
