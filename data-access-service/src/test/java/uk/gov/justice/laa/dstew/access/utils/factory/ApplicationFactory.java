package uk.gov.justice.laa.dstew.access.utils.factory;

import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

import java.time.InstantSource;
import java.util.*;
import java.util.function.Consumer;

public class ApplicationFactory {

    public static Application create() {
        Application entity = Application.builder()
                .id(UUID.randomUUID())
                .build();

        Map<String, Object> appContent = new HashMap<>();
        appContent.put("test", "content");
        entity.setApplicationContent(appContent);

        return entity;
    }

    public static Application create(Consumer<Application.Builder> customiser) {
        Application entity = create();
        Application.Builder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }

    public static List<Application> create(int number) {
        ArrayList<Application> entities = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            entities.add(create());
        }
        return entities;
    }

    public static List<Application> create(int number, Consumer<Application.Builder> customiser) {
        ArrayList<Application> entities = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            entities.add(create(customiser));
        }
        return entities;
    }
}