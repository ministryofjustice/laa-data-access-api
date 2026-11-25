package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.ApportionmentInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventJson;

/** Splits Prior Authority submission and decision events into kept fields and content. */
class PriorAuthorityEventContentTest {

  private static final Instant AT = Instant.parse("2026-11-02T10:00:00Z");

  private final EventJson json = new EventJson();
  private final EventContentSeparator separator =
      new EventContentSeparator(
          json,
          Map.of(
              "access.PriorAuthoritySubmitted",
              PriorAuthoritySubmittedEvent.class,
              "access.PriorAuthorityDecisionMade",
              PriorAuthorityDecisionMadeEvent.class));

  @Test
  void givenSubmittedEvent_whenSeparated_thenSubmittedContentIsContentAndRejoinRestoresIt() {
    var event =
        new PriorAuthoritySubmittedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "EXPERT",
            1,
            0L,
            3L,
            AT,
            "HOURLY",
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Jane Doe", null, null, null),
            "{\"justification\":\"Jane Doe\"}");

    var split = separator.separate("access.PriorAuthoritySubmitted", tree(event));

    assertThat(split.content().propertyNames())
        .containsExactlyInAnyOrder("content", "serialisedRequest");
    assertThat(split.kept().toString()).doesNotContain("Jane Doe");
    assertThat(rejoin(split, PriorAuthoritySubmittedEvent.class)).isEqualTo(event);
    assertThat(separator.hasContentFields("access.PriorAuthoritySubmitted")).isTrue();
  }

  @Test
  void givenDecisionEvent_whenSeparated_thenDecisionDetailsAreContentAndRejoinRestoresThem() {
    var event =
        new PriorAuthorityDecisionMadeEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "EXPERT",
            1L,
            "GRANTED",
            "Justified",
            new BigDecimal("125.50"),
            AT,
            AT,
            new ExpertFeeInformation(
                new BigDecimal("10.00"), new BigDecimal("20.50"), new TimeRequested(3, 15)),
            new DisbursementInformation(new BigDecimal("30.00")),
            new ApportionmentInformation(new BigDecimal("40.00")));

    var split = separator.separate("access.PriorAuthorityDecisionMade", tree(event));

    assertThat(split.content().propertyNames())
        .containsExactlyInAnyOrder(
            "decisionJustification",
            "amountGranted",
            "dateGranted",
            "expertFee",
            "disbursementInformation",
            "apportionmentInformation");
    assertThat(split.kept().get("overallDecision").asText()).isEqualTo("GRANTED");
    assertThat(rejoin(split, PriorAuthorityDecisionMadeEvent.class))
        .usingRecursiveComparison()
        .isEqualTo(event);
    assertThat(separator.hasContentFields("access.PriorAuthorityDecisionMade")).isTrue();
  }

  private ObjectNode tree(Object event) {
    return json.mapper().valueToTree(event);
  }

  private <T> T rejoin(EventContentSeparator.Split split, Class<T> type) {
    return json.mapper().treeToValue(separator.rejoin(split.kept(), split.content()), type);
  }
}
