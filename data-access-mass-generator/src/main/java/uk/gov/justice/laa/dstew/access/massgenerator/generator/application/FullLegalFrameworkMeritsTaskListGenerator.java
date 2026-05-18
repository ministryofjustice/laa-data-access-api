package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullLegalFrameworkMeritsTaskList;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullLegalFrameworkMeritsTaskListGenerator
    extends BaseGenerator<
        FullLegalFrameworkMeritsTaskList,
        FullLegalFrameworkMeritsTaskList.FullLegalFrameworkMeritsTaskListBuilder> {

  private static final String SERIALIZED_DATA =
      """
      ---
      - :name: :involved_children
        :state: :complete
      - :name: :opponents_application
        :state: :complete
      - :name: :domestic_abuse_summary
        :state: :not_applicable
      - :name: :statement_of_case
        :state: :complete
      - :name: :attempts_to_settle
        :state: :complete
      - :name: :urgency
        :state: :complete
      - :name: :children_proceeding_brought_by_LA
        :state: :complete
      """;

  public FullLegalFrameworkMeritsTaskListGenerator() {
    super(
        FullLegalFrameworkMeritsTaskList::toBuilder,
        FullLegalFrameworkMeritsTaskList.FullLegalFrameworkMeritsTaskListBuilder::build);
  }

  private String randomInstant() {
    return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
  }

  @Override
  public FullLegalFrameworkMeritsTaskList createDefault() {
    return FullLegalFrameworkMeritsTaskList.builder()
        .id(UUID.randomUUID().toString())
        .legalAidApplicationId(UUID.randomUUID().toString())
        .serializedData(SERIALIZED_DATA)
        .createdAt(randomInstant())
        .updatedAt(randomInstant())
        .build();
  }
}
