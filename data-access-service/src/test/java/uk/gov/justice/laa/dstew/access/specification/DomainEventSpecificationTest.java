package uk.gov.justice.laa.dstew.access.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;

import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class DomainEventSpecificationTest {

    @SuppressWarnings("unchecked")
    private final Root<DomainEventEntity> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder builder = mock(CriteriaBuilder.class);

    @Test
    void givenApplicationId_whenToPredicate_thenReturnPredicate() {
        UUID appId = UUID.randomUUID();
        Specification<DomainEventEntity> spec = DomainEventSpecification.filterApplicationId(appId);

        Path<Object> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get("applicationId")).thenReturn(path);
        when(builder.equal(path, appId)).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isEqualTo(predicate);
    }

    @Test
    void givenNullEventTypes_whenToPredicate_thenReturnNull() {
        Specification<DomainEventEntity> spec = DomainEventSpecification.filterEventTypes(null);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }

    @Test
    void givenEmptyEventTypes_whenToPredicate_thenReturnNull() {
        Specification<DomainEventEntity> spec = DomainEventSpecification.filterEventTypes(List.of());

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isNull();
    }

    @Test
    void givenEventTypes_whenToPredicate_thenReturnPredicate() {
        List<DomainEventType> eventTypes = List.of(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER);
        Path<Object> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get("type")).thenReturn(path);
        when(path.in(eventTypes)).thenReturn(predicate);

        Specification<DomainEventEntity> spec = DomainEventSpecification.filterEventTypes(eventTypes);

        Predicate result = spec.toPredicate(root, query, builder);

        assertThat(result).isEqualTo(predicate);
    }
}