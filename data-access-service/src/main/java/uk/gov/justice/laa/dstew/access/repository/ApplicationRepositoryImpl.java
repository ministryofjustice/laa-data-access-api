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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity_;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity_;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity_;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity_;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.IndividualSummaryDto;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/** Implementation of custom application summary repository using CriteriaBuilder. */
@ExcludeFromGeneratedCodeCoverage
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
   * Named ordinal positions of each selected column in the {@code Object[]} rows returned by {@code
   * executeDataQuery}. The declaration order here must exactly match the argument order passed to
   * {@code query.multiselect(...)}.
   */
  private enum Col {
    /** Application ID — see {@link ApplicationEntity#getId()}. */
    ID,
    /** Application status — see {@link ApplicationEntity#getStatus()}. */
    STATUS,
    /** LAA reference string — see {@link ApplicationEntity#getLaaReference()}. */
    LAA_REFERENCE,
    /** Office code — see {@link ApplicationEntity#getOfficeCode()}. */
    OFFICE_CODE,
    /** Timestamp the application was created — see {@link ApplicationEntity#getCreatedAt()}. */
    CREATED_AT,
    /**
     * Timestamp the application was last modified — see {@link ApplicationEntity#getModifiedAt()}.
     */
    MODIFIED_AT,
    /** Timestamp the application was submitted — see {@link ApplicationEntity#getSubmittedAt()}. */
    SUBMITTED_AT,
    /**
     * Whether delegated functions were used — see {@link
     * ApplicationEntity#getUsedDelegatedFunctions()}.
     */
    USED_DELEGATED_FUNCTIONS,
    /** Category of law — see {@link ApplicationEntity#getCategoryOfLaw()}. */
    CATEGORY_OF_LAW,
    /** Matter type — see {@link ApplicationEntity#getMatterType()}. */
    MATTER_TYPE,
    /**
     * Whether the application was auto-granted — see {@link ApplicationEntity#getIsAutoGranted()}.
     */
    IS_AUTO_GRANTED,
    /** ID of the assigned caseworker (may be {@code null} if unassigned). */
    CASEWORKER_ID,
    /** {@code true} if this application is the lead of a linked-application group. */
    IS_LEAD,
    /**
     * Individual ID — {@code null} when the LEFT JOIN finds no individual — see {@link
     * IndividualEntity#getId()}.
     */
    INDIVIDUAL_ID,
    /** Individual first name — see {@link IndividualEntity#getFirstName()}. */
    INDIVIDUAL_FIRST_NAME,
    /** Individual last name — see {@link IndividualEntity#getLastName()}. */
    INDIVIDUAL_LAST_NAME,
    /** Individual date of birth — see {@link IndividualEntity#getDateOfBirth()}. */
    INDIVIDUAL_DATE_OF_BIRTH,
    /** Individual type (e.g. CLIENT) — see {@link IndividualEntity#getType()}. */
    INDIVIDUAL_TYPE;

    /** Returns {@link #ordinal()} for use as an {@code Object[]} array index. */
    int idx() {
      return ordinal();
    }
  }

  /**
   * Fetches application data with caseworker and all individuals in a single query using
   * CriteriaQuery&lt;Object[]&gt;. Rows are grouped in Java to produce one {@link
   * ApplicationSummaryDto} per application, each holding a list of {@link IndividualSummaryDto}.
   */
  private List<ApplicationSummaryDto> executeDataQuery(List<UUID> ids, Pageable pageable) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
    Root<ApplicationEntity> root = query.from(ApplicationEntity.class);

    // Join all individuals — no type filter — to avoid fanning out only for CLIENTs
    Join<ApplicationEntity, IndividualEntity> individualJoin =
        root.join(ApplicationEntity_.individuals, JoinType.LEFT);
    Join<ApplicationEntity, CaseworkerEntity> caseworkerJoin =
        root.join(ApplicationEntity_.caseworker, JoinType.LEFT);

    Expression<Boolean> isLeadExpr = getIsLeadExpr(query, cb, root);

    query.multiselect(
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
        isLeadExpr,
        individualJoin.get(IndividualEntity_.id),
        individualJoin.get(IndividualEntity_.firstName),
        individualJoin.get(IndividualEntity_.lastName),
        individualJoin.get(IndividualEntity_.dateOfBirth),
        individualJoin.get(IndividualEntity_.type));

    query.where(root.get(ApplicationEntity_.id).in(ids));

    applySort(cb, root, query, pageable);

    List<Object[]> rows = entityManager.createQuery(query).getResultList();
    return groupRows(rows, ids);
  }

  /**
   * Groups raw Object[] rows (one per individual) into one {@link ApplicationSummaryDto} per
   * application, preserving the page-sort order dictated by {@code ids}.
   */
  private List<ApplicationSummaryDto> groupRows(List<Object[]> rows, List<UUID> ids) {
    Map<UUID, ApplicationSummaryDto> byId = new LinkedHashMap<>();

    for (Object[] row : rows) {
      UUID appId = (UUID) row[Col.ID.idx()];
      ApplicationSummaryDto dto =
          byId.computeIfAbsent(
              appId,
              id -> {
                ApplicationSummaryDto d = new ApplicationSummaryDto();
                d.setId(id);
                d.setStatus((ApplicationStatus) row[Col.STATUS.idx()]);
                d.setLaaReference((String) row[Col.LAA_REFERENCE.idx()]);
                d.setOfficeCode((String) row[Col.OFFICE_CODE.idx()]);
                d.setCreatedAt((Instant) row[Col.CREATED_AT.idx()]);
                d.setModifiedAt((Instant) row[Col.MODIFIED_AT.idx()]);
                d.setSubmittedAt((Instant) row[Col.SUBMITTED_AT.idx()]);
                d.setUsedDelegatedFunctions((Boolean) row[Col.USED_DELEGATED_FUNCTIONS.idx()]);
                d.setCategoryOfLaw((CategoryOfLaw) row[Col.CATEGORY_OF_LAW.idx()]);
                d.setMatterType((MatterType) row[Col.MATTER_TYPE.idx()]);
                d.setIsAutoGranted((Boolean) row[Col.IS_AUTO_GRANTED.idx()]);
                d.setCaseworkerId((UUID) row[Col.CASEWORKER_ID.idx()]);
                Boolean isLead = (Boolean) row[Col.IS_LEAD.idx()];
                d.setLead(isLead != null && isLead);
                d.setIndividuals(new ArrayList<>());
                return d;
              });

      // INDIVIDUAL_ID is null when the LEFT JOIN found no individual for this application
      if (row[Col.INDIVIDUAL_ID.idx()] != null) {
        IndividualSummaryDto individual =
            IndividualSummaryDto.builder()
                .id((UUID) row[Col.INDIVIDUAL_ID.idx()])
                .firstName((String) row[Col.INDIVIDUAL_FIRST_NAME.idx()])
                .lastName((String) row[Col.INDIVIDUAL_LAST_NAME.idx()])
                .dateOfBirth((LocalDate) row[Col.INDIVIDUAL_DATE_OF_BIRTH.idx()])
                .type((IndividualType) row[Col.INDIVIDUAL_TYPE.idx()])
                .build();
        dto.getIndividuals().add(individual);
      }
    }

    // Sort each application's individuals list by ID for a consistent, deterministic order.
    byId.values()
        .forEach(
            dto -> dto.getIndividuals().sort(Comparator.comparing(IndividualSummaryDto::getId)));

    // Restore page-sort order (rows come back ordered by sort, but LinkedHashMap insertion order
    // already preserves that; re-ordering against ids handles any gaps from the LEFT JOIN fan-out)
    List<ApplicationSummaryDto> ordered = new ArrayList<>(ids.size());
    for (UUID id : ids) {
      ApplicationSummaryDto dto = byId.get(id);
      if (dto != null) {
        ordered.add(dto);
      }
    }
    return ordered;
  }

  private static Expression<Boolean> getIsLeadExpr(
      CriteriaQuery<?> query, CriteriaBuilder cb, Root<ApplicationEntity> root) {
    Subquery<Integer> leadSubquery = query.subquery(Integer.class);
    Root<LinkedApplicationEntity> leadRoot = leadSubquery.from(LinkedApplicationEntity.class);
    leadSubquery.select(cb.literal(1));
    leadSubquery.where(
        cb.equal(
            leadRoot.get(LinkedApplicationEntity_.leadApplicationId),
            root.get(ApplicationEntity_.id)));

    return cb.selectCase().when(cb.exists(leadSubquery), true).otherwise(false).as(Boolean.class);
  }

  /** Executes a separate count query to determine the total number of matching applications. */
  private long executeCountQuery(Specification<ApplicationEntity> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<ApplicationEntity> countRoot = countQuery.from(ApplicationEntity.class);

    Predicate predicate = spec.toPredicate(countRoot, countQuery, cb);
    if (predicate != null) {
      countQuery.where(predicate).select(cb.countDistinct(countRoot));
    } else {
      countQuery.select(cb.count(countRoot));
    }

    return entityManager.createQuery(countQuery).getSingleResult();
  }

  private void applySort(
      CriteriaBuilder cb, Root<ApplicationEntity> root, CriteriaQuery<?> query, Pageable pageable) {
    List<Order> orders = new ArrayList<>();
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
