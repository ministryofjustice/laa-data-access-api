package uk.gov.justice.laa.dstew.access.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/** Implementation of custom application summary repository using CriteriaBuilder. */
public class ApplicationSummaryRepositoryImpl implements ApplicationSummaryRepositoryCustom {

  private final EntityManager entityManager;

  public ApplicationSummaryRepositoryImpl(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public Page<ApplicationSummaryDto> findAllAsDtos(
      Specification<ApplicationEntity> spec, Pageable pageable) {
    List<ApplicationSummaryDto> results = executeDataQuery(spec, pageable);
    long count = executeCountQuery(spec);
    return new PageImpl<>(results, pageable, count);
  }

  private List<ApplicationSummaryDto> executeDataQuery(
      Specification<ApplicationEntity> spec, Pageable pageable) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<ApplicationSummaryDto> query = cb.createQuery(ApplicationSummaryDto.class);
    Root<ApplicationEntity> root = query.from(ApplicationEntity.class);

    Join<ApplicationEntity, CaseworkerEntity> caseworkerJoin =
        root.join("caseworker", JoinType.LEFT);

    Join<ApplicationEntity, IndividualEntity> individualsJoin =
        root.join("individuals", JoinType.LEFT);
    individualsJoin.on(cb.equal(individualsJoin.get("type"), IndividualType.CLIENT));

    Expression<Boolean> isLead =
        cb.<Boolean>selectCase()
            .when(
                cb.greaterThan(cb.size(root.<List<ApplicationEntity>>get("linkedApplications")), 0),
                true)
            .otherwise(false);

    query.select(
        cb.construct(
            ApplicationSummaryDto.class,
            root.get("id"),
            root.get("status"),
            root.get("laaReference"),
            root.get("officeCode"),
            root.get("createdAt"),
            root.get("modifiedAt"),
            root.get("submittedAt"),
            root.get("usedDelegatedFunctions"),
            root.get("categoryOfLaw"),
            root.get("matterType"),
            root.get("isAutoGranted"),
            caseworkerJoin.get("id"),
            individualsJoin.get("firstName"),
            individualsJoin.get("lastName"),
            individualsJoin.get("dateOfBirth"),
            isLead));

    query.distinct(true);

    Predicate predicate = spec.toPredicate(root, query, cb);
    if (predicate != null) {
      query.where(predicate);
    }

    applySort(cb, root, query, pageable);

    TypedQuery<ApplicationSummaryDto> typedQuery = entityManager.createQuery(query);
    typedQuery.setFirstResult((int) pageable.getOffset());
    typedQuery.setMaxResults(pageable.getPageSize());
    return typedQuery.getResultList();
  }

  private long executeCountQuery(Specification<ApplicationEntity> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<ApplicationEntity> countRoot = countQuery.from(ApplicationEntity.class);

    Predicate predicate = spec.toPredicate(countRoot, countQuery, cb);
    if (predicate != null) {
      countQuery.where(predicate);
    }

    countQuery.select(cb.countDistinct(countRoot));
    return entityManager.createQuery(countQuery).getSingleResult();
  }

  private void applySort(
      CriteriaBuilder cb, Root<ApplicationEntity> root, CriteriaQuery<?> query, Pageable pageable) {
    if (pageable.getSort().isSorted()) {
      List<Order> orders =
          pageable.getSort().stream()
              .map(
                  order ->
                      order.isAscending()
                          ? cb.asc(root.get(order.getProperty()))
                          : cb.desc(root.get(order.getProperty())))
              .toList();
      query.orderBy(orders);
    }
  }
}
