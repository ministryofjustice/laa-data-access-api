package uk.gov.justice.laa.dstew.access.utils.factory.application;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.Individual;
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
                .individualContent(Map.of(
                        "test", "content"
                ))
                .build();

        CaseworkerEntity caseworkerEntity = CaseworkerEntity.builder()
                .username("JohnDoe")
                .build();

        return ApplicationEntity.builder()
                .createdAt(InstantSource.system().instant())
                .status(ApplicationStatus.IN_PROGRESS)
                .modifiedAt(InstantSource.system().instant())
                .individuals(Set.of(individualEntity))
                .applicationContent(Map.of(
                        "test", "content"
                ))
                .caseworker(caseworkerEntity)
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