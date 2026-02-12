package uk.gov.justice.laa.dstew.access.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;

public interface ApplicationRepositoryCustom {
  Page<ApplicationSummary> findApplicationSummaries(
      Specification<ApplicationEntity> spec, Pageable pageable);
}
