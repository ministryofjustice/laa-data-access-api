package uk.gov.justice.laa.dstew.access.utils.factory.application;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.time.InstantSource;
import java.util.*;
import java.util.function.Consumer;

@Component
public class ApplicationFactoryImpl implements Factory<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder> {

    @Override
    public ApplicationEntity create() {
        ApplicationEntity entity = ApplicationEntity.builder()
                .createdAt(InstantSource.system().instant())
                .id(UUID.randomUUID())
                .status(ApplicationStatus.IN_PROGRESS)
                .modifiedAt(InstantSource.system().instant())
                .build();

        Map<String, Object> appContent = new HashMap<>();
        appContent.put("test", "content");
        entity.setApplicationContent(appContent);

        return entity;
    }

    @Override
    public ApplicationEntity create(Consumer<ApplicationEntity.ApplicationEntityBuilder> customiser) {
        ApplicationEntity entity = create();
        ApplicationEntity.ApplicationEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}