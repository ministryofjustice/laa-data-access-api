package uk.gov.justice.laa.dstew.access.utils.factory.application;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.Status;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
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
                .build();

        var instant = InstantSource.system().instant();
        var instantWithoutNanos = instant.minusNanos(instant.getNano());
        return ApplicationEntity.builder()
                .createdAt(instantWithoutNanos)
                .status(Status.IN_PROGRESS)
                .modifiedAt(instantWithoutNanos)
                .submittedAt(instantWithoutNanos)
                .individuals(new HashSet<>(Set.of(individualEntity)))
                .applicationContent(new LinkedHashMap<>(Map.of(
                        "test", "content"
                )))
                .caseworker(BaseIntegrationTest.CaseworkerJohnDoe)
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