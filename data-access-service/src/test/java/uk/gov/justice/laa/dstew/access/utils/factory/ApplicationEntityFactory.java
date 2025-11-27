package uk.gov.justice.laa.dstew.access.utils.factory;

import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

import java.time.InstantSource;
import java.util.*;
import java.util.function.Consumer;

public class ApplicationEntityFactory {

    public static ApplicationEntity create() {
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

    public static ApplicationEntity create(Consumer<ApplicationEntity.ApplicationEntityBuilder> customiser) {
        ApplicationEntity entity = create();
        ApplicationEntity.ApplicationEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }

    public static List<ApplicationEntity> create(int number) {
        ArrayList<ApplicationEntity> entities = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            entities.add(create());
        }
        return entities;
    }

    public static List<ApplicationEntity> create(int number, Consumer<ApplicationEntity.ApplicationEntityBuilder> customiser) {
        ArrayList<ApplicationEntity> entities = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            entities.add(create(customiser));
        }
        return entities;
    }
}