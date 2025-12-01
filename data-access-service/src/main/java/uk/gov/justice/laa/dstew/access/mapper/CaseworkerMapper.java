package uk.gov.justice.laa.dstew.access.mapper;

import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.model.Caseworker;

/**
 * Maps between caseworker entity and caseworker API model.
 */
@Mapper(componentModel = "spring")
public interface CaseworkerMapper {
  Caseworker toCaseworker(CaseworkerEntity entity);
}
