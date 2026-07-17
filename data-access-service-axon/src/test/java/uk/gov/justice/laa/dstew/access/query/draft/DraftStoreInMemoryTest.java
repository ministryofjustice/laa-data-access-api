package uk.gov.justice.laa.dstew.access.query.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import uk.gov.justice.laa.dstew.access.AxonInMemoryConfig;
import uk.gov.justice.laa.dstew.access.DataAccessServiceAxonApplication;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.SynchronousApplicationIndividual;

@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "axon.eventstore.jpa.enabled=false",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.datasource.url=jdbc:h2:mem:axon-drafts;DB_CLOSE_DELAY=-1"
    })
@Import(AxonInMemoryConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DraftStoreInMemoryTest {

  @Autowired private DraftRepository draftRepository;

  @Test
  void givenStoredDraft_whenUpdatedInPlace_thenPersistsMutation() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    DraftRecord draft =
        DraftRecord.builder()
            .eventId(eventId)
            .applyApplicationId(applyApplicationId)
            .draftType(DraftType.CIVIL_APPLICATION)
            .data(
                new DraftData(
                    null,
                    List.of(
                        new SynchronousApplicationIndividual(
                            UUID.randomUUID(), "Ada", "Lovelace", null, null, "CLIENT")),
                    List.of()))
            .createdAt(Instant.parse("2026-07-16T09:00:00Z"))
            .build();
    draftRepository.saveAndFlush(draft);

    DraftRecord stored = draftRepository.findById(eventId).orElseThrow();
    stored.setData(
        new DraftData(
            null,
            List.of(
                new SynchronousApplicationIndividual(
                    UUID.randomUUID(), "Grace", "Hopper", null, null, "CLIENT")),
            List.of()));
    draftRepository.saveAndFlush(stored);

    DraftRecord reloaded = draftRepository.findById(eventId).orElseThrow();
    assertThat(reloaded.getData().individuals()).hasSize(1);
    assertThat(reloaded.getData().individuals().get(0).firstName()).isEqualTo("Grace");
    assertThat(draftRepository.count()).isEqualTo(1);
  }
}
