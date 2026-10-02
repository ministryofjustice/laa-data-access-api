package uk.gov.justice.laa.dstew.access.query.utils.security;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

/** Combines functional and access predicates before a protected repository is executed. */
@Component
public class AccessAwareQueryExecutor {

  public <T> Optional<T> findOne(
      JpaSpecificationExecutor<T> repository,
      Specification<T> functionalSpecification,
      Specification<T> accessSpecification) {
    return repository.findOne(Specification.where(functionalSpecification).and(accessSpecification));
  }

  public <T> Page<T> findAll(
      JpaSpecificationExecutor<T> repository,
      Specification<T> functionalSpecification,
      Pageable pageable,
      Specification<T> accessSpecification) {
    return repository.findAll(
        Specification.where(functionalSpecification).and(accessSpecification), pageable);
  }

  public <T> List<T> findAll(
      JpaSpecificationExecutor<T> repository,
      Specification<T> functionalSpecification,
      Specification<T> accessSpecification) {
    return repository.findAll(Specification.where(functionalSpecification).and(accessSpecification));
  }
}


