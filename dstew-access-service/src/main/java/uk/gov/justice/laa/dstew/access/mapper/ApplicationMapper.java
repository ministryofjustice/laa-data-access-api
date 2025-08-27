package uk.gov.justice.laa.dstew.access.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationHistoryEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistory;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceeding;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingUpdateRequest;
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
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "proceedings", ignore = true)
  @Mapping(target = "recordHistory", ignore = true)
  ApplicationEntity toApplicationEntity(ApplicationCreateRequest applicationCreateReq);

  /**
   * Maps the given application request to an application entity.
   *
   * @param applicationEntity the application entity
   * @param applicationUpdateReq the application update request
   */
  @BeanMapping(nullValuePropertyMappingStrategy =  NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "proceedings", ignore = true)
  @Mapping(target = "recordHistory", ignore = true)
  void updateApplicationEntity(
          @MappingTarget ApplicationEntity applicationEntity,
          ApplicationUpdateRequest applicationUpdateReq);

  @BeanMapping(nullValuePropertyMappingStrategy =  NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  void updateApplicationEntity(
          @MappingTarget Application application,
          ApplicationUpdateRequest applicationUpdateReq);

  /**
   * This mapping exists solely so we can declare the ignored fields, to avoid a warning on the
   * updateApplicationEntity mapping method which targets an Application instance.
   */
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  ApplicationProceeding toApplicationProceeding(ApplicationProceedingUpdateRequest applicationProceedingUpdateReq);

  ApplicationHistory toApplicationHistory(
          ApplicationHistoryEntity applicationHistoryEntity);

  default OffsetDateTime toOffsetDateTime(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}