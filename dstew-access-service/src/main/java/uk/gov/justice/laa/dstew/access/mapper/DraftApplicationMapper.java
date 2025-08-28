package uk.gov.justice.laa.dstew.access.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import uk.gov.justice.laa.dstew.access.entity.DraftApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.DraftApplication;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationUpdateRequest;

/**
 * The mapper between DraftApplication and DraftApplicationEntity.
 */
@Mapper(componentModel = "spring")
public interface DraftApplicationMapper {

  /**
   * Maps the given application to an application entity.
   *
   * @param draftApplicationCreateReq the application
   * @return the application entity
  */
  @Mapping(target = "id", ignore = true)
  DraftApplicationEntity toDraftApplicationEntity(DraftApplicationCreateRequest draftApplicationCreateReq);

  /**
   * Maps the given application to an application entity.
   *
   * @param draftApplicationUpdateReq the application
   * @return the application entity
   */
  @Mapping(target = "id", ignore = true)
  DraftApplicationEntity toDraftApplicationEntity(DraftApplicationUpdateRequest draftApplicationUpdateReq);

  /**
   * Maps the given application entity to an application.
   *
   * @param draftApplicationEntity the application entity
   * @return the application
   */
  DraftApplication toDraftApplication(DraftApplicationEntity draftApplicationEntity);

  /**
   * Maps the given application request to an application entity.
   *
   * @param applicationEntity the application entity
   * @param applicationUpdateReq the application update request
   */
  @BeanMapping(nullValuePropertyMappingStrategy =  NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  void updateApplicationEntity(
          @MappingTarget DraftApplicationEntity applicationEntity,
          DraftApplicationUpdateRequest applicationUpdateReq);
}
