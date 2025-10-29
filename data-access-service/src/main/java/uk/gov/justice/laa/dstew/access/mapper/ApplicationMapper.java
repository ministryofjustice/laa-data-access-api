package uk.gov.justice.laa.dstew.access.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
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
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "proceedings", ignore = true)
    @Mapping(target = "recordHistory", ignore = true)
    void updateApplicationEntity(
            @MappingTarget ApplicationEntity applicationEntity,
            ApplicationUpdateRequest applicationUpdateReq);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateApplicationEntity(
            @MappingTarget Application application,
            ApplicationUpdateRequest applicationUpdateReq);

    /**
     * Maps ApplicationProceedingUpdateRequest to ApplicationProceeding.
     */
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ApplicationProceeding toApplicationProceeding(ApplicationProceedingUpdateRequest applicationProceedingUpdateReq);

    /**
     * Maps ApplicationProceedingEntity to ApplicationProceeding.
     * This enables MapStruct to map lists of proceedings automatically.
     */
    @Mapping(target = "createdAt", source = "recordHistory.createdAt")
    @Mapping(target = "createdBy", source = "recordHistory.createdBy")
    @Mapping(target = "updatedAt", source = "recordHistory.updatedAt")
    @Mapping(target = "updatedBy", source = "recordHistory.updatedBy")
    ApplicationProceeding toApplicationProceeding(ApplicationProceedingEntity entity);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /**
     * Optional helper to map a list of ApplicationProceedingEntity to ApplicationProceeding.
     * Usually MapStruct handles this automatically.
     */
    default List<ApplicationProceeding> toApplicationProceedings(List<ApplicationProceedingEntity> entities) {
        return entities == null ? null : entities.stream()
                .map(this::toApplicationProceeding)
                .toList();
    }
}
