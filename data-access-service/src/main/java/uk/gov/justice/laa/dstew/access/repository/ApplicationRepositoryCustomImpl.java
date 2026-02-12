package uk.gov.justice.laa.dstew.access.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationSummaryMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResult;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

public class ApplicationRepositoryCustomImpl implements ApplicationRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private ApplicationSummaryMapper mapper;

  @Override
  public Page<ApplicationSummary> findApplicationSummaries(
      Specification<ApplicationEntity> spec, Pageable pageable) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // Main query
    CriteriaQuery<ApplicationSummaryResult> cq = cb.createQuery(ApplicationSummaryResult.class);
    Root<ApplicationEntity> root = cq.from(ApplicationEntity.class);

    Join<ApplicationEntity, IndividualEntity> clientJoin =
        root.join("individuals", JoinType.LEFT);
    clientJoin.on(cb.equal(clientJoin.get("type"), IndividualType.CLIENT));

    Join<ApplicationEntity, CaseworkerEntity> caseworkerJoin =
        root.join("caseworker", JoinType.LEFT);

    cq.select(cb.construct(
        ApplicationSummaryResult.class,
        root.get("id"),
        root.get("laaReference"),
        root.get("status"),
        root.get("submittedAt"),
        root.get("isAutoGranted"),
        root.get("categoryOfLaw"),
        root.get("matterType"),
        root.get("usedDelegatedFunctions"),
        root.get("officeCode"),
        caseworkerJoin.get("id"),
        clientJoin.get("firstName"),
        clientJoin.get("lastName"),
        clientJoin.get("dateOfBirth"),
        root.get("modifiedAt")
    ));

    if (spec != null) {
      Predicate predicate = spec.toPredicate(root, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // Sorting
    if (pageable.getSort().isSorted()) {
      List<Order> orders = pageable.getSort().stream()
          .map(o -> o.isAscending()
              ? cb.asc(root.get(o.getProperty()))
              : cb.desc(root.get(o.getProperty())))
          .toList();
      cq.orderBy(orders);
    }

    List<ApplicationSummaryResult> results = entityManager.createQuery(cq)
        .setFirstResult((int) pageable.getOffset())
        .setMaxResults(pageable.getPageSize())
        .getResultList();

    List<ApplicationSummary> summaries = results.stream()
        .map(mapper::toApplicationSummary)
        .toList();

    // Count query
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<ApplicationEntity> countRoot = countQuery.from(ApplicationEntity.class);
    countQuery.select(cb.count(countRoot));

    if (spec != null) {
      Predicate predicate = spec.toPredicate(countRoot, countQuery, cb);
      if (predicate != null) {
        countQuery.where(predicate);
      }
    }

    long total = entityManager.createQuery(countQuery).getSingleResult();

    return new PageImpl<>(summaries, pageable, total);
  }
}
