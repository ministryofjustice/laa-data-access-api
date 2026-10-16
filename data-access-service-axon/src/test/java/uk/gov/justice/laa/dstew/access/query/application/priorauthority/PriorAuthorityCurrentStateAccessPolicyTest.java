package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.UnrestrictedReadAccessScope;

class PriorAuthorityCurrentStateAccessPolicyTest {

  private final PriorAuthorityCurrentStateAccessPolicy policy =
      new PriorAuthorityCurrentStateAccessPolicy();

  @Test
  void givenUnrestrictedScope_whenRestricted_thenProducesConjunction() {
    CriteriaBuilder criteriaBuilder = Mockito.mock(CriteriaBuilder.class);
    Predicate predicate = Mockito.mock(Predicate.class);
    when(criteriaBuilder.conjunction()).thenReturn(predicate);

    policy
        .restrictionFor(new UnrestrictedReadAccessScope())
        .toPredicate(null, null, criteriaBuilder);

    verify(criteriaBuilder).conjunction();
  }

  @Test
  void givenEmptyOfficeScope_whenRestricted_thenProducesDisjunction() {
    CriteriaBuilder criteriaBuilder = Mockito.mock(CriteriaBuilder.class);
    Predicate predicate = Mockito.mock(Predicate.class);
    when(criteriaBuilder.disjunction()).thenReturn(predicate);

    policy
        .restrictionFor(new OfficeCodeReadAccessScope(Set.of()))
        .toPredicate(null, null, criteriaBuilder);

    verify(criteriaBuilder).disjunction();
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenProviderOfficeScope_whenRestricted_thenFiltersByOfficeCode() {
    Root<PriorAuthorityReadModel> root = Mockito.mock(Root.class);
    Path<String> officeCode = Mockito.mock(Path.class);
    CriteriaBuilder criteriaBuilder = Mockito.mock(CriteriaBuilder.class);
    when(root.<String>get("officeCode")).thenReturn(officeCode);

    policy
        .restrictionFor(new OfficeCodeReadAccessScope(Set.of("OFFICE-1")))
        .toPredicate(root, null, criteriaBuilder);

    verify(root).get("officeCode");
    verify(officeCode).in(Set.of("OFFICE-1"));
  }
}
