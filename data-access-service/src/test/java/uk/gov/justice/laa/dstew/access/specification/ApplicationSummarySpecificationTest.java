package uk.gov.justice.laa.dstew.access.specification;

import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApplicationSummarySpecificationTest {
    @SuppressWarnings("unchecked")
    private final Root<ApplicationSummaryEntity> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class);


    @Test
    void shouldNotFailWhenIsStatusFilter(){
    /*
        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification.isStatus("ACTIVE");

        Predicate summaryPredicate = mock(Predicate.class);

        when(root.get("statusCodeLookupEntity"))
                .thenReturn(mock(jakarta.persistence.criteria.Path.class));
        when(statusCodeEntityPath.get("code"))
                .thenReturn(mock(jakarta.persistence.criteria.Path.class));

        when(builder.equal(any(), any())).thenReturn(summaryPredicate);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNotNull();

     */
    }

    @Test
    void shouldNotFailWhenIsApplicationReferenceFilter(){
        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification.isApplicationReference("ref1");

        Predicate summaryPredicate = mock(Predicate.class);

        when(root.get("applicationReference"))
                .thenReturn(mock(jakarta.persistence.criteria.Path.class));

        when(builder.equal(any(), any())).thenReturn(summaryPredicate);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNotNull();
    }
    /*
      public static Specification<Submission> submissionIdEqualTo(final String submissionId) {
    return (root, query, cb) ->
        Optional.ofNullable(submissionId).isPresent()
            ? cb.equal(root.get("id"), UUID.fromString(submissionId))
            : cb.conjunction();
  }

    @Test
  void shouldBuildSpecificationWithSubmissionId() {
    UUID submissionId = Uuid7.timeBasedUuid();
    Specification<Submission> spec =
        SubmissionSpecification.submissionIdEqualTo(submissionId.toString());

    Predicate withSubmissionId = Mockito.mock(Predicate.class);

    Mockito.when(root.get("id")).thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(cb.equal(Mockito.any(), Mockito.eq(submissionId))).thenReturn(withSubmissionId);

    var actualResults = spec.toPredicate(root, query, cb);

    assertThat(actualResults).isNotNull();
  }
     */
}
