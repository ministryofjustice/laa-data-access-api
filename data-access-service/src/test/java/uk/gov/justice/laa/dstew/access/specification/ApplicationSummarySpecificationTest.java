package uk.gov.justice.laa.dstew.access.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class ApplicationSummarySpecificationTest {
    @SuppressWarnings("unchecked")
    private final Root<ApplicationSummaryEntity> root = mock(Root.class);

    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

    @Test
    void givenAllEmpty_whenToPredicate_thenReturnNull() {

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, null, null, null, null);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }

    @Test
    void givenReference_whenToPredicate_thenReturnPredicate() {

        String reference = "some-reference";
        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, reference, null, null, null);

        Predicate summaryPredicate = mock(Predicate.class);
        when(builder.like(any(), eq("%"+reference+"%"))).thenReturn(summaryPredicate);
        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNotNull();
    }

    @Test
    void givenBlankReference_whenToPredicate_thenReturnNull() {

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, "", null, null, null);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }

    @Test
    void givenApplicationStatus_whenToPredicate_thenReturnPredicate(){

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(ApplicationStatus.IN_PROGRESS, null, null, null, null);

        Predicate summaryPredicate = mock(Predicate.class);

        when(root.get("status"))
                .thenReturn(mock(jakarta.persistence.criteria.Path.class));
        when(builder.equal(any(), eq(ApplicationStatus.IN_PROGRESS))).thenReturn(summaryPredicate);

        Predicate result = spec.toPredicate(root, query, builder);
        assertThat(result).isNotNull();
    }

    @Test
    void givenFirstName_whenToPredicate_thenReturnPredicate() {

        String firstName = "some-name";
        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, null, firstName, null, null);

        Predicate summaryPredicate = mock(Predicate.class);

        when(
                root.join(eq("individuals"), eq(JoinType.INNER))
                )
        .thenReturn(mock());

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
    void givenBlankFirstName_whenToPredicate_thenReturnNull() {

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, null, "", null, null);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }

    @Test
    void givenBlankLastName_whenToPredicate_thenReturnNull() {

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, null, null, "", null);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }

    @Test
    void givenLastName_whenToPredicate_thenReturnPredicate() {

        String lastName = "some-name";
        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, null, null, lastName, null);

        Predicate summaryPredicate = mock(Predicate.class);

        when(
                root.join(eq("individuals"), eq(JoinType.INNER))
        )
                .thenReturn(mock());

        when(
                builder.like(
                        any(),
                        eq("%"+lastName+"%")
                )
        ).thenReturn(summaryPredicate);
        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNotNull();
    }

    @Test
    void givenCaseworkerId_whenToPredicate_thenReturnPredicate() {

        UUID caseworkerId = UUID.randomUUID();

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, null, null, null, caseworkerId);

        Predicate summaryPredicate = mock(Predicate.class);
        when(
                root.join(eq("caseworker"), eq(JoinType.INNER))
        )
                .thenReturn(mock());

        when(
                builder.equal(
                        any(),
                        eq(caseworkerId)
                )
        ).thenReturn(summaryPredicate);

        Predicate result = spec.toPredicate(root, query, builder);
        assertThat(result).isNotNull();
    }

    @Test
    void givenNullCaseworkerId_whenToPredicate_thenReturnNull() {

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(null, null, null, null, null);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }

    @Test
    void givenAllFilters_whenToPredicate_thenReturnPredicate() {
        ApplicationStatus status = ApplicationStatus.SUBMITTED;
        String reference = "ref2";
        String firstName = "Andy";
        String lastName = "Green";
        UUID caseworkerId = UUID.randomUUID();

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification
                .filterBy(status, reference, firstName, lastName, caseworkerId);

        Join<Object,Object> individualsJoin = mock(Join.class);
        Join<Object,Object> caseworkerJoin = mock(Join.class);
        Predicate referencePredicate = mock(Predicate.class);
        Predicate firstNamePredicate = mock(Predicate.class);
        Predicate lastNamePredicate = mock(Predicate.class);
        Predicate caseworkerPredicate = mock(Predicate.class);

        when(root.get("status")).thenReturn(mock(Path.class));
        when(root.get("laaReference")).thenReturn(mock(Path.class));
        when(root.join(eq("individuals"), eq(JoinType.INNER))).thenReturn(individualsJoin);
        when(root.join(eq("caseworker"), eq(JoinType.INNER))).thenReturn(caseworkerJoin);

        when(builder.equal(any(), eq(status))).thenReturn(mock(Predicate.class));
        when(builder.like(any(), eq("%" + reference + "%"))).thenReturn(referencePredicate);
        when(builder.like(any(), eq("%" + firstName + "%"))).thenReturn(firstNamePredicate);
        when(builder.like(any(), eq("%" + lastName + "%"))).thenReturn(lastNamePredicate);
        when(builder.equal(any(), eq(caseworkerId))).thenReturn(caseworkerPredicate);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNotNull();
    }
}
