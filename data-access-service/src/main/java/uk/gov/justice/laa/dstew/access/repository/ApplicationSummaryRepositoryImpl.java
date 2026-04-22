package uk.gov.justice.laa.dstew.access.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    long count = executeCountQuery(spec);
    if (count == 0) {
      return new PageImpl<>(List.of(), pageable, 0);
    }

    List<UUID> ids = executeIdQuery(spec, pageable);
    if (ids.isEmpty()) {
      return new PageImpl<>(List.of(), pageable, count);
    }

    List<ApplicationSummaryDto> results = executeDataQuery(ids, pageable);
    Map<UUID, ClientInfo> clientMap = fetchClientInfo(ids);
    Set<UUID> leadIds = fetchLeadIds(ids);

    for (ApplicationSummaryDto dto : results) {
      ClientInfo client = clientMap.get(dto.getId());
      if (client != null) {
        dto.setClientFirstName(client.firstName());
        dto.setClientLastName(client.lastName());
        dto.setClientDateOfBirth(client.dateOfBirth());
      }
      dto.setLead(leadIds.contains(dto.getId()));
    }

    return new PageImpl<>(results, pageable, count);
  }

  /** Fetches application IDs matching the spec, with pagination and sorting. */
  private List<UUID> executeIdQuery(Specification<ApplicationEntity> spec, Pageable pageable) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<UUID> query = cb.createQuery(UUID.class);
    Root<ApplicationEntity> root = query.from(ApplicationEntity.class);

    // Use a subquery with the spec to get matching IDs (handles deduplication from spec joins)
    Subquery<UUID> subquery = query.subquery(UUID.class);
    Root<ApplicationEntity> subRoot = subquery.from(ApplicationEntity.class);
    subquery.select(subRoot.get("id")).distinct(true);

    Predicate predicate = spec.toPredicate(subRoot, query, cb);
    if (predicate != null) {
      subquery.where(predicate);
    }

    // Outer query selects from the root, filtered by the subquery IDs
    query.select(root.get("id"));
    query.where(root.get("id").in(subquery));

    applySort(cb, root, query, pageable);

    TypedQuery<UUID> typedQuery = entityManager.createQuery(query);
    typedQuery.setFirstResult((int) pageable.getOffset());
    typedQuery.setMaxResults(pageable.getPageSize());
    return typedQuery.getResultList();
  }

  /** Fetches application data with caseworker join only — no one-to-many joins, no DISTINCT. */
  private List<ApplicationSummaryDto> executeDataQuery(List<UUID> ids, Pageable pageable) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<ApplicationSummaryDto> query = cb.createQuery(ApplicationSummaryDto.class);
    Root<ApplicationEntity> root = query.from(ApplicationEntity.class);

    Join<ApplicationEntity, CaseworkerEntity> caseworkerJoin =
        root.join("caseworker", JoinType.LEFT);

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
            caseworkerJoin.get("id")));

    query.where(root.get("id").in(ids));

    applySort(cb, root, query, pageable);

    return entityManager.createQuery(query).getResultList();
  }

  /** Fetches client individual info for the given application IDs. */
  private Map<UUID, ClientInfo> fetchClientInfo(List<UUID> applicationIds) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
    Root<ApplicationEntity> root = query.from(ApplicationEntity.class);

    Join<ApplicationEntity, IndividualEntity> individualJoin =
        root.join("individuals", JoinType.INNER);

    query.multiselect(
        root.get("id"),
        individualJoin.get("firstName"),
        individualJoin.get("lastName"),
        individualJoin.get("dateOfBirth"));

    query.where(
        cb.and(
            root.get("id").in(applicationIds),
            cb.equal(individualJoin.get("type"), IndividualType.CLIENT)));

    return entityManager.createQuery(query).getResultList().stream()
        .collect(
            Collectors.toMap(
                row -> (UUID) row[0],
                row -> new ClientInfo((String) row[1], (String) row[2], (LocalDate) row[3]),
                (a, b) -> a));
  }

  /** Fetches which of the given application IDs are lead applications. */
  private Set<UUID> fetchLeadIds(List<UUID> applicationIds) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<UUID> query = cb.createQuery(UUID.class);
    Root<ApplicationEntity> root = query.from(ApplicationEntity.class);

    root.join("linkedApplications", JoinType.INNER);

    query.select(root.get("id")).distinct(true);
    query.where(root.get("id").in(applicationIds));

    return Set.copyOf(entityManager.createQuery(query).getResultList());
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
    orders.add(cb.asc(root.get("createdAt")));
    query.orderBy(orders);
  }

  private record ClientInfo(String firstName, String lastName, LocalDate dateOfBirth) {}
}
