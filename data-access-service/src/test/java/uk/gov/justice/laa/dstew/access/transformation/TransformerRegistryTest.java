package uk.gov.justice.laa.dstew.access.transformation;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class TransformerRegistryTest {

    static class DummyDto {}
    static class OtherDto {}

    static class DummyTransformer implements ResponseTransformer<DummyDto> {
        @Override
        public DummyDto transform(DummyDto response) {
            return null;
        }
    }
    static class OtherTransformer implements ResponseTransformer<OtherDto> {
        @Override
        public OtherDto transform(OtherDto response) {
            return null;
        }
    }

    @Test
    void givenTransformerListWithOneTransformer_whenHasTransformer_thenReturnTrue() {
        TransformerRegistry registry = new TransformerRegistry(List.of(new DummyTransformer()));
        assertThat(registry.hasTransformer(DummyDto.class)).isTrue();
    }

    @Test
    void givenTransformerListWithNoMatchingTransformer_whenHasTransformer_thenReturnFalse() {
        TransformerRegistry registry = new TransformerRegistry(List.of(new DummyTransformer()));
        assertThat(registry.hasTransformer(OtherDto.class)).isFalse();
    }

    @Test
    void givenTransformerListWithOneTransformer_whenGetTransformer_thenReturnTransformer() {
        DummyTransformer transformer = new DummyTransformer();
        TransformerRegistry registry = new TransformerRegistry(List.of(transformer));
        Optional<ResponseTransformer<DummyDto>> result = registry.getTransformer(DummyDto.class);
        assertThat(result).contains(transformer);
    }

    @Test
    void givenTransformerListWithNoMatchingTransformer_whenGetTransformer_thenReturnEmptyOptional() {
        TransformerRegistry registry = new TransformerRegistry(List.of(new DummyTransformer()));
        Optional<ResponseTransformer<OtherDto>> result = registry.getTransformer(OtherDto.class);
        assertThat(result).isEmpty();
    }

    @Test
    void givenEmptyTransformerList_whenHasTransformer_thenReturnFalse() {
        TransformerRegistry registry = new TransformerRegistry(List.of());
        assertThat(registry.hasTransformer(DummyDto.class)).isFalse();
    }

    @Test
    void givenEmptyTransformerList_whenGetTransformer_thenReturnEmptyOptional() {
        TransformerRegistry registry = new TransformerRegistry(List.of());
        Optional<ResponseTransformer<DummyDto>> result = registry.getTransformer(DummyDto.class);
        assertThat(result).isEmpty();
    }
}

