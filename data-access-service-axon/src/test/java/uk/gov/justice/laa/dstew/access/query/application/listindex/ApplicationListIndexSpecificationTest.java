package uk.gov.justice.laa.dstew.access.query.application.listindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsQuery;

class ApplicationListIndexSpecificationTest {

  private Root<ApplicationListIndexReadModel> root;
  private CriteriaQuery<?> criteriaQuery;
  private CriteriaBuilder cb;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    root = mock(Root.class);
    criteriaQuery = mock(CriteriaQuery.class);
    cb = mock(CriteriaBuilder.class);

    // Stub root.get() to return a mock path for any field
    Path mockPath = mock(Path.class);
    when(root.get(any(String.class))).thenReturn(mockPath);

    // Stub lower() for case-insensitive name predicates
    Expression<String> loweredExpr = mock(Expression.class);
    when(cb.lower(any())).thenReturn(loweredExpr);

    // Stub individual predicate builders to return non-null predicates
    Predicate equalPredicate = mock(Predicate.class);
    Predicate likePredicate = mock(Predicate.class);
    Predicate andPredicate = mock(Predicate.class);
    when(cb.equal(any(), any())).thenReturn(equalPredicate);
    when(cb.like(any(Expression.class), any(String.class))).thenReturn(likePredicate);
    when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);
  }

  @Test
  void givenAllFiltersNull_whenBuilt_thenProducesAndWithNoPredicates() {
    FindAllApplicationsQuery query =
        new FindAllApplicationsQuery(null, null, null, null, null, null, null, null, 1, 20);

    Specification<ApplicationListIndexReadModel> spec =
        ApplicationListIndexSpecification.from(query);
    Predicate result = spec.toPredicate(root, criteriaQuery, cb);

    assertThat(result).isNotNull();
    // cb.and() was called with an empty array — the AND of zero predicates
    verify(cb).and(new Predicate[0]);
  }

  @Test
  void givenStatusFilter_whenBuilt_thenAddsEqualPredicate() {
    FindAllApplicationsQuery query =
        new FindAllApplicationsQuery(
            "APPLICATION_SUBMITTED", null, null, null, null, null, null, null, 1, 20);

    ApplicationListIndexSpecification.from(query).toPredicate(root, criteriaQuery, cb);

    verify(root).get("status");
    verify(cb).equal(any(), eq("APPLICATION_SUBMITTED"));
  }

  @Test
  void givenLaaReferenceFilter_whenBuilt_thenAddsEqualPredicate() {
    FindAllApplicationsQuery query =
        new FindAllApplicationsQuery(null, "LAA-123", null, null, null, null, null, null, 1, 20);

    ApplicationListIndexSpecification.from(query).toPredicate(root, criteriaQuery, cb);

    verify(root).get("laaReference");
    verify(cb).equal(any(), eq("LAA-123"));
  }

  @Test
  void givenMatterTypeFilter_whenBuilt_thenAddsEqualPredicate() {
    FindAllApplicationsQuery query =
        new FindAllApplicationsQuery(null, null, "MEDIATION", null, null, null, null, null, 1, 20);

    ApplicationListIndexSpecification.from(query).toPredicate(root, criteriaQuery, cb);

    verify(root).get("matterType");
    verify(cb).equal(any(), eq("MEDIATION"));
  }

  @Test
  void givenClientFirstNameFilter_whenBuilt_thenAddsLowercaseLikePredicate() {
    FindAllApplicationsQuery query =
        new FindAllApplicationsQuery(null, null, null, "Jane", null, null, null, null, 1, 20);

    ApplicationListIndexSpecification.from(query).toPredicate(root, criteriaQuery, cb);

    verify(root).get("clientFirstName");
    verify(cb).lower(any());
    verify(cb).like(any(Expression.class), eq("jane%"));
  }

  @Test
  void givenClientLastNameFilter_whenBuilt_thenAddsLowercaseLikePredicate() {
    FindAllApplicationsQuery query =
        new FindAllApplicationsQuery(null, null, null, null, "Smith", null, null, null, 1, 20);

    ApplicationListIndexSpecification.from(query).toPredicate(root, criteriaQuery, cb);

    verify(root).get("clientLastName");
    verify(cb).lower(any());
    verify(cb).like(any(Expression.class), eq("smith%"));
  }

  @Test
  void givenClientDateOfBirthFilter_whenBuilt_thenAddsEqualPredicate() {
    LocalDate dob = LocalDate.of(1990, 6, 15);
    FindAllApplicationsQuery query =
        new FindAllApplicationsQuery(null, null, null, null, null, dob, null, null, 1, 20);

    ApplicationListIndexSpecification.from(query).toPredicate(root, criteriaQuery, cb);

    verify(root).get("clientDateOfBirth");
    verify(cb).equal(any(), eq(dob));
  }

  @Test
  void givenAutoGrantedFilter_whenBuilt_thenAddsEqualPredicate() {
    FindAllApplicationsQuery query =
        new FindAllApplicationsQuery(
            null, null, null, null, null, null, AutoGrantedState.MANUAL, null, null, 1, 20);

    ApplicationListIndexSpecification.from(query).toPredicate(root, criteriaQuery, cb);

    verify(root).get("autoGranted");
    verify(cb).equal(any(), eq(AutoGrantedState.MANUAL));
  }

  @Test
  void givenMultipleFilters_whenBuilt_thenCombinesAllPredicatesIntoAnd() {
    FindAllApplicationsQuery query =
        new FindAllApplicationsQuery(
            "APPLICATION_SUBMITTED",
            "LAA-123",
            "MEDIATION",
            null,
            "Smith",
            null,
            null,
            null,
            1,
            20);

    ApplicationListIndexSpecification.from(query).toPredicate(root, criteriaQuery, cb);

    // Three filters: status, laaReference, matterType + clientLastName = 4 predicates
    verify(cb).equal(any(), eq("APPLICATION_SUBMITTED"));
    verify(cb).equal(any(), eq("LAA-123"));
    verify(cb).equal(any(), eq("MEDIATION"));
    verify(cb).like(any(Expression.class), eq("smith%"));
  }
}
