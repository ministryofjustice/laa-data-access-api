package uk.gov.justice.laa.dstew.access.eventcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ErasableTest {

  private final EventJson json = new EventJson();

  @Test
  void givenNullValue_whenWrapped_thenErased() {
    assertThat(Erasable.of(null)).isSameAs(Erasable.erased());
    assertThat(Erasable.orErased(null)).isSameAs(Erasable.erased());
    assertThat(Erasable.orErased(Erasable.of("x"))).isEqualTo(Erasable.of("x"));
  }

  @Test
  void givenErased_whenRead_thenThrowsAndFallsBack() {
    Erasable<String> erased = Erasable.erased();

    assertThat(erased.isErased()).isTrue();
    assertThatThrownBy(erased::value)
        .isInstanceOf(ContentErasedException.class)
        .hasMessage("Content has been erased");
    assertThat(erased.orElse("fallback")).isEqualTo("fallback");
    assertThat(erased.map(String::length).isErased()).isTrue();
    assertThat(erased).hasToString("Erasable[erased]");
  }

  @Test
  void givenPresent_whenRead_thenReturnsValueWithoutRevealingItInToString() {
    Erasable<String> present = Erasable.of("secret");

    assertThat(present.isErased()).isFalse();
    assertThat(present.value()).isEqualTo("secret");
    assertThat(present.orElse("fallback")).isEqualTo("secret");
    assertThat(present.map(String::length)).isEqualTo(Erasable.of(6));
    assertThat(present).hasToString("Erasable[present]");
    assertThat(present).hasSameHashCodeAs(Erasable.of("secret")).isNotEqualTo("secret");
  }

  @Test
  void givenEvent_whenRoundTrippedAsJson_thenOnlyValueIsWrittenAndAbsenceReadsAsErased() {
    var event = new ContentTestEvent("id", Erasable.of("secret"));

    String written = json.mapper().writeValueAsString(event);

    assertThat(written).isEqualTo("{\"id\":\"id\",\"note\":\"secret\"}");
    assertThat(json.mapper().readValue(written, ContentTestEvent.class)).isEqualTo(event);
    assertThat(json.mapper().readValue("{\"id\":\"id\"}", ContentTestEvent.class).note().isErased())
        .isTrue();
  }
}
