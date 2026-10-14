package uk.gov.justice.laa.dstew.access.command.application.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;

@ExtendWith(MockitoExtension.class)
class ApplicationUpdateDetailsFactoryTest {

  @Mock private ApplicationContentParser applicationContentParser;
  @InjectMocks private ApplicationUpdateDetailsFactory factory;

  @Test
  void givenLeadProceeding_whenPrepared_thenCarriesLegalCategoryCodes() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content = Map.of("proceedings", List.of());
    ParsedAppContentDetails parsed =
        ParsedAppContentDetails.builder()
            .categoryOfLaw("Family")
            .matterType("Children Act")
            .categoryOfLawCode("MAT")
            .matterTypeCode("KPBLW")
            .submittedAt(Instant.parse("2026-07-14T12:30:00Z"))
            .proceedings(List.of())
            .build();
    when(applicationContentParser.parse(content)).thenReturn(parsed);
    UpdateApplicationCommand command =
        new UpdateApplicationCommand(
            applicationId, "APPLICATION_SUBMITTED", content, "{}", Instant.now());
    ApplicationDataPayload current =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));

    ApplicationDataPayload updated = factory.prepare(command, current, false);

    assertThat(updated.categoryOfLawCode()).isEqualTo("MAT");
    assertThat(updated.matterTypeCode()).isEqualTo("KPBLW");
  }
}
