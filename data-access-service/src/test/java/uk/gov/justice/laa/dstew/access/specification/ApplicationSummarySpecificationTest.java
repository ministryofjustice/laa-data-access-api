package uk.gov.justice.laa.dstew.access.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;

@ExtendWith(MockitoExtension.class)
public class ApplicationSummarySpecificationTest {

    @Test
    void shouldisStatus(){
        /*
        Specification<ApplicationSummaryEntity> spec = ApplicationSummarySpecification.isStatus("ACTIVE");
        Root<ApplicationSummaryEntity> root = null;
        CriteriaQuery<ApplicationSummaryEntity> query = null;
        CriteriaBuilder builder = null;
        spec.toPredicate(root, query, builder);

         */
    }
}
