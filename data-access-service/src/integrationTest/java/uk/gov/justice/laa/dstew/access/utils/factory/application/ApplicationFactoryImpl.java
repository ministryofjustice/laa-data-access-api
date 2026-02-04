package uk.gov.justice.laa.dstew.access.utils.factory.application;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.DateTimeHelper;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.time.InstantSource;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

@Component
public class ApplicationFactoryImpl implements Factory<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder> {

    @Override
    public ApplicationEntity create() {

        IndividualEntity individualEntity = IndividualEntity.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .individualContent(new LinkedHashMap<>(Map.of(
                        "test", "content"
                )))
                .type(IndividualType.CLIENT)
                .build();
        var instant = DateTimeHelper.GetSystemInstanceWithoutNanoseconds();
        return ApplicationEntity.builder()
                .applyApplicationId(UUID.randomUUID())
                .createdAt(instant)
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .modifiedAt(instant)
                .submittedAt(instant)
                .individuals(new HashSet<>(Set.of(individualEntity)))
                .applicationContent(new LinkedHashMap<>(Map.of(
                        "test", "content"
                )))
                .caseworker(BaseIntegrationTest.CaseworkerJohnDoe)
                .usedDelegatedFunctions(false)
                .isAutoGranted(true)
                .categoryOfLaw(CategoryOfLaw.FAMILY)
                .matterType(MatterType.SCA)
                .build();
    }

    @Override
    public ApplicationEntity create(Consumer<ApplicationEntity.ApplicationEntityBuilder> customiser) {
        ApplicationEntity entity = create();
        ApplicationEntity.ApplicationEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}