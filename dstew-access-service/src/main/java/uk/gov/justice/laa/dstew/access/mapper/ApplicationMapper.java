package uk.gov.justice.laa.dstew.access.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;

/**
 * The mapper between Application and ApplicationEntity.
 */
@Mapper(componentModel = "spring")
public interface ApplicationMapper {

  /**
   * Maps the given application entity to an application.
   *
   * @param applicationEntity the application entity
   * @return the application
   */
  Application toApplication(ApplicationEntity applicationEntity);

  /**
   * Maps the given application to an application entity.
   *
   * @param applicationCreateReq the application
   * @return the application entity
   */
  ApplicationEntity toApplicationEntity(ApplicationCreateRequest applicationCreateReq);

  /**
   * Maps the given application request to an application entity.
   *
   * @param applicationEntity the application entity
   * @param applicationUpdateReq the application update request
   */
  @BeanMapping(nullValuePropertyMappingStrategy =  NullValuePropertyMappingStrategy.IGNORE)
  void updateApplicationEntity(
      @MappingTarget ApplicationEntity applicationEntity,
      ApplicationUpdateRequest applicationUpdateReq);

  /*
  @BeanMapping(nullValuePropertyMappingStrategy =  NullValuePropertyMappingStrategy.IGNORE)
  void updateApplicationEntity(
      @MappingTarget Application application,
      ApplicationUpdateRequest applicationUpdateReq);
  */
}
