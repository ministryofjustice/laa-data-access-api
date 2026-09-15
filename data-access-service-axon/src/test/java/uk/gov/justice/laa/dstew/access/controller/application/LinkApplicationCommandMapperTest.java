package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkType;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;

class LinkApplicationCommandMapperTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-15T09:30:00Z");

  private final Clock fixedClock = Clock.fixed(OCCURRED_AT, ZoneOffset.UTC);
  private final LinkApplicationCommandMapper mapper = new LinkApplicationCommandMapper(fixedClock);

  @Test
  void givenValidRequest_whenMapped_thenPreservesDirectionalIds() {
    UUID sourceApplicationId = UUID.randomUUID();
    UUID targetApplicationId = UUID.randomUUID();
    var request = new ApplicationLinkRequest(targetApplicationId, ApplicationLinkType.FAMILY);

    var command = mapper.toCommand(sourceApplicationId, request);

    assertThat(command.sourceApplicationId()).isEqualTo(sourceApplicationId);
    assertThat(command.targetApplicationId()).isEqualTo(targetApplicationId);
  }

  @Test
  void givenFamilyLinkType_whenMapped_thenMapsToDomainLinkType() {
    var request = new ApplicationLinkRequest(UUID.randomUUID(), ApplicationLinkType.FAMILY);

    var command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.linkType()).isEqualTo(LinkType.FAMILY);
  }

  @Test
  void givenFixedClock_whenMapped_thenOccurredAtMatchesClock() {
    var request = new ApplicationLinkRequest(UUID.randomUUID(), ApplicationLinkType.FAMILY);

    var command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
  }
}
