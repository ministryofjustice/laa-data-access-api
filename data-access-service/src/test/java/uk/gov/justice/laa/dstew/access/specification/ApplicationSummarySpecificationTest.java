package uk.gov.justice.laa.dstew.access.specification;

import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApplicationSummarySpecificationTest {
    @SuppressWarnings("unchecked")
    private final Root<ApplicationSummaryEntity> root = mock(Root.class);

    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

    @Test
    void shouldNotFailWhenReferenceIsNotBlank(){

        String reference = "some-reference";
        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, reference, null, null);

        Predicate summaryPredicate = mock(Predicate.class);
        when(builder.like(any(), eq("%"+reference+"%"))).thenReturn(summaryPredicate);
        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldNotFailWhenReferenceIsBlank(){

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, "", null, null);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }

    @Test
    void shouldNotFailWhenStatusHasAValue(){

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(ApplicationStatus.IN_PROGRESS, null, null, null);

        Predicate summaryPredicate = mock(Predicate.class);

        when(root.get("status"))
                .thenReturn(mock(jakarta.persistence.criteria.Path.class));
        when(builder.equal(any(), eq(ApplicationStatus.IN_PROGRESS))).thenReturn(summaryPredicate);

        Predicate result = spec.toPredicate(root, query, builder);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldFailWhenStatusHasAValueThatDoesNotMatch(){

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(ApplicationStatus.SUBMITTED, null, null, null);

        Predicate summaryPredicate = mock(Predicate.class);

        when(root.get("status"))
                .thenReturn(mock(jakarta.persistence.criteria.Path.class));
        when(builder.equal(any(), eq(ApplicationStatus.IN_PROGRESS))).thenReturn(summaryPredicate);

        Predicate result = spec.toPredicate(root, query, builder);
        assertThat(result).isNull();
    }
    @Test
    void shouldNotFailWhenAllFieldsAreNull(){

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, null, null, null);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }

    @Test
    void shouldNotFailWhenAllFieldsAreSet(){

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(ApplicationStatus.SUBMITTED, "ref2", "Andy", "Green");

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }

    @Test
    void shouldNotFailWhenFirstNameIsNotBlank(){

        String firstName = "some-name";
        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, null, firstName, null);

        Predicate summaryPredicate = mock(Predicate.class);

        when(
                builder.like(
                        any(),
                        eq("%"+firstName+"%")
                )
            ).thenReturn(summaryPredicate);
        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldNotFailWhenFirstNameIsBlank(){

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, null, "", null);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }

    @Test
    void shouldNotFailWhenLastNameIsBlank(){

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, null, null, "");

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }
}
