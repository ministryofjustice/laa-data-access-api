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
      "---\n"
          + "- :name: :involved_children\n"
          + "  :state: :complete\n"
          + "- :name: :opponents_application\n"
          + "  :state: :complete\n"
          + "- :name: :domestic_abuse_summary\n"
          + "  :state: :not_applicable\n"
          + "- :name: :statement_of_case\n"
          + "  :state: :complete\n"
          + "- :name: :attempts_to_settle\n"
          + "  :state: :complete\n"
          + "- :name: :urgency\n"
          + "  :state: :complete\n"
          + "- :name: :children_proceeding_brought_by_LA\n"
          + "  :state: :complete\n";

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
