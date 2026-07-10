package uk.gov.justice.laa.dstew.access.utils.generator.getallapplications;

import java.time.LocalDate;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.GetAllApplicationsQuery;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generates {@link GetAllApplicationsQuery} instances for use in tests. */
public class GetAllApplicationsQueryGenerator
    extends BaseGenerator<
        GetAllApplicationsQuery, GetAllApplicationsQuery.GetAllApplicationsQueryBuilder> {

  /** Constructs the generator. */
  public GetAllApplicationsQueryGenerator() {
    super(
        GetAllApplicationsQuery::toBuilder,
        GetAllApplicationsQuery.GetAllApplicationsQueryBuilder::build);
  }

  @Override
  public GetAllApplicationsQuery createDefault() {
    return GetAllApplicationsQuery.builder()
        .status("APPLICATION_SUBMITTED")
        .laaReference("REF7327")
        .clientFirstName("Jane")
        .clientLastName("Doe")
        .clientDateOfBirth(LocalDate.of(1990, 1, 1))
        .userId(UUID.randomUUID())
        .isAutoGranted(false)
        .matterType("SPECIAL_CHILDREN_ACT")
        .sortBy("SUBMITTED_DATE")
        .orderBy("ASC")
        .page(1)
        .pageSize(10)
        .build();
  }
}
