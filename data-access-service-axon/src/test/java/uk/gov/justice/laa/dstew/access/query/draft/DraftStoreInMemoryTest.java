package uk.gov.justice.laa.dstew.access.query.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import uk.gov.justice.laa.dstew.access.AxonInMemoryConfig;
import uk.gov.justice.laa.dstew.access.DataAccessServiceAxonApplication;

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
    DraftRecord draft =
        DraftRecord.builder()
            .applyApplicationId(applyApplicationId)
            .content(Map.of("laaReference", "LAA-DRAFT-1"))
            .createdAt(Instant.parse("2026-07-16T09:00:00Z"))
            .updatedAt(Instant.parse("2026-07-16T09:00:00Z"))
            .build();
    draftRepository.saveAndFlush(draft);

    DraftRecord stored = draftRepository.findById(applyApplicationId).orElseThrow();
    stored.setContent(Map.of("laaReference", "LAA-DRAFT-2"));
    stored.setUpdatedAt(Instant.parse("2026-07-16T10:00:00Z"));
    draftRepository.saveAndFlush(stored);

    DraftRecord reloaded = draftRepository.findById(applyApplicationId).orElseThrow();
    assertThat(reloaded.getContent()).containsEntry("laaReference", "LAA-DRAFT-2");
    assertThat(reloaded.getCreatedAt()).isEqualTo(Instant.parse("2026-07-16T09:00:00Z"));
    assertThat(reloaded.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-16T10:00:00Z"));
    assertThat(draftRepository.count()).isEqualTo(1);
  }
}
