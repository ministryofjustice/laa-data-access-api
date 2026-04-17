package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.LinkedList;
import uk.gov.justice.laa.dstew.access.model.ApplicationMerits;
import uk.gov.justice.laa.dstew.access.model.OpponentDetails;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

import java.util.List;
import java.util.Map;

public class ApplicationMeritsGenerator extends BaseGenerator<ApplicationMerits, ApplicationMerits.ApplicationMeritsBuilder> {
    private final OpponentDetailsGenerator opponentDetailsGenerator = new OpponentDetailsGenerator();

    public ApplicationMeritsGenerator() {
        super(ApplicationMerits::toBuilder, ApplicationMerits.ApplicationMeritsBuilder::build);
    }

    @Override
    public ApplicationMerits createDefault() {
        return ApplicationMerits.builder()
                .opponents(new LinkedList<OpponentDetails>(List.of(opponentDetailsGenerator.createDefault())))
                .involvedChildren(
                    List.of(
                        Map.of("first_name", "John",
                                "last_name", "Smith",
                                "date_of_birth", "Mon Aug 20 2022 20:20:00 GMT+0100 (British Summer Time)")
                    ))
                .build();
    }

    @Override
    public ApplicationMerits createRandom() {
        int childrenCount = faker.number().numberBetween(1, 4);
        List<Map<String, Object>> children = new java.util.ArrayList<>();

        for (int i = 0; i < childrenCount; i++) {
            children.add(Map.of(
                "first_name", faker.name().firstName(),
                "last_name", faker.name().lastName(),
                "date_of_birth", faker.timeAndDate().past(6570, java.util.concurrent.TimeUnit.DAYS).toString()
            ));
        }

        int opponentsCount = faker.number().numberBetween(0, 3);
        List<OpponentDetails> opponents = new LinkedList<>();
        for (int i = 0; i < opponentsCount; i++) {
            opponents.add(opponentDetailsGenerator.createRandom());
        }

        return ApplicationMerits.builder()
                .opponents(opponents)
                .involvedChildren(children)
                .build();
    }
}