package uk.gov.justice.laa.dstew.access.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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

        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification.isStatus("ACTIVE");
        Predicate summaryPredicate = mock(Predicate.class);
        when(root.get("statusCodeLookupEntity")).thenReturn(mock(jakarta.persistence.criteria.Path.class));
        when(builder.equal(any(), any())).thenReturn(summaryPredicate);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNotNull();
    }
}
