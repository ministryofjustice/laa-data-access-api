package uk.gov.justice.laa.dstew.access.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;

/** Custom repository for fetching application summary data as a projected DTO. */
public interface ApplicationSummaryRepositoryCustom {

  Page<ApplicationSummaryDto> findAllAsDtos(
      Specification<ApplicationEntity> spec, Pageable pageable);
}
