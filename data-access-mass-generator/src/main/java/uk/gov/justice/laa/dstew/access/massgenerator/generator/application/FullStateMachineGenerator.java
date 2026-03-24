package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullStateMachine;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullStateMachineGenerator extends BaseGenerator<FullStateMachine, FullStateMachine.FullStateMachineBuilder> {

    public FullStateMachineGenerator() {
        super(FullStateMachine::toBuilder, FullStateMachine.FullStateMachineBuilder::build);
    }

    private String randomInstant() {
        return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
    }

    @Override
    public FullStateMachine createDefault() {
        return FullStateMachine.builder()
                .id(UUID.randomUUID().toString())
                .legalAidApplicationId(UUID.randomUUID().toString())
                .type(faker.options().option("SpecialChildrenActStateMachine", "MeritsStateMachine"))
                .aasmState(faker.options().option("generating_reports", "submitted", "draft", "checking_merits_answers"))
                .createdAt(randomInstant())
                .updatedAt(randomInstant())
                .ccmsReason(null)
                .build();
    }
}

