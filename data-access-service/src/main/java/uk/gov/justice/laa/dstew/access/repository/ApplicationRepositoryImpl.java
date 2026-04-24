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
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity_;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity_;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity_;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity_;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/** Implementation of custom application summary repository using CriteriaBuilder. */
public class ApplicationRepositoryImpl implements ApplicationSummaryRepositoryCustom {

  private final EntityManager entityManager;

  public ApplicationRepositoryImpl(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public Page<ApplicationSummaryDto> findAllAsDtos(
      Specification<ApplicationEntity> spec, Pageable pageable) {
    long count = executeCountQuery(spec);
    if (count == 0) {
      return new PageImpl<>(List.of(), pageable, 0);
    }

    List<UUID> ids = executeIdQuery(spec, pageable);
    if (ids.isEmpty()) {
      return new PageImpl<>(List.of(), pageable, count);
    }

    List<ApplicationSummaryDto> results = executeDataQuery(ids, pageable);

    return new PageImpl<>(results, pageable, count);
  }

  /**
   * Fetches application IDs matching the spec, with pagination and sorting. Uses a subquery to
   * isolate filtering joins from the outer sort/pagination, avoiding duplicate rows from joins
   * affecting OFFSET/FETCH results.
   */
  private List<UUID> executeIdQuery(Specification<ApplicationEntity> spec, Pageable pageable) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<UUID> query = cb.createQuery(UUID.class);
    Root<ApplicationEntity> outerRoot = query.from(ApplicationEntity.class);
    query.select(outerRoot.get(ApplicationEntity_.id));

    // Build the spec predicate against a subquery root so that any joins the spec introduces
    // don't cause duplicate rows in the outer OFFSET/FETCH result.
    Subquery<UUID> subquery = query.subquery(UUID.class);
    Root<ApplicationEntity> subRoot = subquery.from(ApplicationEntity.class);
    Predicate predicate = spec.toPredicate(subRoot, query, cb);

    if (predicate != null) {
      subquery.select(subRoot.get(ApplicationEntity_.id)).distinct(true).where(predicate);
      query.where(outerRoot.get(ApplicationEntity_.id).in(subquery));
    }
    // else: no filters/joins needed — query the outer root directly, avoiding a no-op subquery.

    applySort(cb, outerRoot, query, pageable);

    TypedQuery<UUID> typedQuery = entityManager.createQuery(query);
    typedQuery.setFirstResult((int) pageable.getOffset());
    typedQuery.setMaxResults(pageable.getPageSize());
    return typedQuery.getResultList();
  }

  /**
   * Fetches application data with caseworker, client individual info, and lead status in a single
   * query.
   */
  private List<ApplicationSummaryDto> executeDataQuery(List<UUID> ids, Pageable pageable) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<ApplicationSummaryDto> query = cb.createQuery(ApplicationSummaryDto.class);
    Root<ApplicationEntity> root = query.from(ApplicationEntity.class);

    Join<ApplicationEntity, CaseworkerEntity> caseworkerJoin =
        root.join(ApplicationEntity_.caseworker, JoinType.LEFT);

    Join<ApplicationEntity, IndividualEntity> clientJoin =
        root.join(ApplicationEntity_.individuals, JoinType.LEFT);
    clientJoin.on(cb.equal(clientJoin.get(IndividualEntity_.type), IndividualType.CLIENT));

    Expression<Boolean> isLeadExpr = getIsLeadExpr(query, cb, root);

    query.select(
        cb.construct(
            ApplicationSummaryDto.class,
            root.get(ApplicationEntity_.id),
            root.get(ApplicationEntity_.status),
            root.get(ApplicationEntity_.laaReference),
            root.get(ApplicationEntity_.officeCode),
            root.get(ApplicationEntity_.createdAt),
            root.get(ApplicationEntity_.modifiedAt),
            root.get(ApplicationEntity_.submittedAt),
            root.get(ApplicationEntity_.usedDelegatedFunctions),
            root.get(ApplicationEntity_.categoryOfLaw),
            root.get(ApplicationEntity_.matterType),
            root.get(ApplicationEntity_.isAutoGranted),
            caseworkerJoin.get(CaseworkerEntity_.id),
            clientJoin.get(IndividualEntity_.firstName),
            clientJoin.get(IndividualEntity_.lastName),
            clientJoin.get(IndividualEntity_.dateOfBirth),
            isLeadExpr));

    query.where(root.get(ApplicationEntity_.id).in(ids));

    applySort(cb, root, query, pageable);

    return entityManager.createQuery(query).getResultList();
  }

  private static Expression<Boolean> getIsLeadExpr(
      CriteriaQuery<ApplicationSummaryDto> query,
      CriteriaBuilder cb,
      Root<ApplicationEntity> root) {
    // EXISTS subquery rooted directly on linked_applications — no need to join back to
    // applications, we already have the outer application id.
    Subquery<Integer> leadSubquery = query.subquery(Integer.class);
    Root<LinkedApplicationEntity> leadRoot = leadSubquery.from(LinkedApplicationEntity.class);
    leadSubquery.select(cb.literal(1));
    leadSubquery.where(
        cb.equal(
            leadRoot.get(LinkedApplicationEntity_.leadApplicationId),
            root.get(ApplicationEntity_.id)));

    return cb.selectCase().when(cb.exists(leadSubquery), true).otherwise(false).as(Boolean.class);
  }

  /**
   * Executes a separate count query to determine the total number of matching applications. This is
   * needed because the ID query applies OFFSET/FETCH for pagination, so its result set cannot be
   * used to derive the total count. Spring's PageImpl requires the total count to calculate total
   * pages and determine whether further pages exist.
   */
  private long executeCountQuery(Specification<ApplicationEntity> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<ApplicationEntity> countRoot = countQuery.from(ApplicationEntity.class);

    Predicate predicate = spec.toPredicate(countRoot, countQuery, cb);
    if (predicate != null) {
      // countDistinct is required because the spec may introduce joins that fan out rows.
      countQuery.where(predicate).select(cb.countDistinct(countRoot));
    } else {
      // No predicate → no joins → count(*) is equivalent and cheaper than count(distinct id).
      countQuery.select(cb.count(countRoot));
    }

    return entityManager.createQuery(countQuery).getSingleResult();
  }

  private void applySort(
      CriteriaBuilder cb, Root<ApplicationEntity> root, CriteriaQuery<?> query, Pageable pageable) {
    List<Order> orders = new java.util.ArrayList<>();
    if (pageable.getSort().isSorted()) {
      pageable.getSort().stream()
          .map(
              order ->
                  order.isAscending()
                      ? cb.asc(root.get(order.getProperty()))
                      : cb.desc(root.get(order.getProperty())))
          .forEach(orders::add);
    }
    // Always add createdAt as a tiebreaker for deterministic, insertion-order pagination
    orders.add(cb.asc(root.get(ApplicationEntity_.createdAt)));
    query.orderBy(orders);
  }
}
