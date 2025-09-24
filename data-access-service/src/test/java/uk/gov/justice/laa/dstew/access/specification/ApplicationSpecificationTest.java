package uk.gov.justice.laa.dstew.access.specification;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;

public class ApplicationSpecificationTest {
    @Test void isPending(){
        Specification<ApplicationEntity> spec = ApplicationSpecification.isPending();
        Root<ApplicationEntity> root;
        CriteriaQuery<ApplicationEntity> query;
        CriteriaBuilder builder;
        spec.toPredicate(root, query, builder);
    }
}
