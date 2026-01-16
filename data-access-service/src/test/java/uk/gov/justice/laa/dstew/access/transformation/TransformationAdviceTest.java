package uk.gov.justice.laa.dstew.access.transformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;

class TransformationAdviceTest {

    private TransformerRegistry transformerRegistry;
    private TransformationAdvice advice;

    @BeforeEach
    void setUp() {
        transformerRegistry = mock(TransformerRegistry.class);
        advice = new TransformationAdvice(transformerRegistry);
    }

    @Test
    void givenTransformerExists_whenSupports_thenReturnTrue() throws NoSuchMethodException {
        Method method = SampleController.class.getMethod("getDto");
        MethodParameter parameter = new MethodParameter(method, -1);

        when(transformerRegistry.hasTransformer(DummyDto.class)).thenReturn(true);

        boolean result = advice.supports(parameter, StringHttpMessageConverter.class);

        assertTrue(result);
    }

    @Test
    void givenNoTransformer_whenSupports_thenReturnFalse() throws NoSuchMethodException {
        Method method = SampleController.class.getMethod("getString");
        MethodParameter parameter = new MethodParameter(method, -1);

        when(transformerRegistry.hasTransformer(String.class)).thenReturn(false);

        boolean result = advice.supports(parameter, StringHttpMessageConverter.class);

        assertFalse(result);
    }

    @Test
    void givenTransformerExists_whenBeforeBodyWrite_thenReturnTransformedObject() {
        DummyDto input = new DummyDto("in");
        DummyDto output = new DummyDto("out");

        ResponseTransformer<DummyDto> transformer = mock(ResponseTransformer.class);
        when(transformer.transform(input)).thenReturn(output);

        when(transformerRegistry.getTransformer(DummyDto.class)).thenReturn(Optional.of(transformer));

        Object result = advice.beforeBodyWrite(
                input, null, MediaType.APPLICATION_JSON, StringHttpMessageConverter.class,
                mock(ServerHttpRequest.class), mock(ServerHttpResponse.class)
        );

        assertEquals(output, result);
    }

    @Test
    void givenTransformerExists_whenBeforeBodyWriteWithList_thenReturnListOfTransformedObjects() {
        DummyDto input = new DummyDto("in");
        DummyDto output = new DummyDto("out");

        ResponseTransformer<DummyDto> transformer = mock(ResponseTransformer.class);
        when(transformer.transform(input)).thenReturn(output);

        when(transformerRegistry.getTransformer(DummyDto.class)).thenReturn(Optional.of(transformer));

        Object result = advice.beforeBodyWrite(
                List.of(input), null, MediaType.APPLICATION_JSON, StringHttpMessageConverter.class,
                mock(ServerHttpRequest.class), mock(ServerHttpResponse.class)
        );

        assertEquals(List.of(output), result);
    }

    @Test
    void givenTransformerReturnsNull_whenBeforeBodyWrite_thenThrowNotFound() {
        DummyDto input = new DummyDto("in");

        ResponseTransformer<DummyDto> transformer = mock(ResponseTransformer.class);
        when(transformer.transform(input)).thenReturn(null);

        when(transformerRegistry.getTransformer(DummyDto.class)).thenReturn(Optional.of(transformer));

        assertThrows(ResponseStatusException.class, () ->
                advice.beforeBodyWrite(
                        input, null, MediaType.APPLICATION_JSON, StringHttpMessageConverter.class,
                        mock(ServerHttpRequest.class), mock(ServerHttpResponse.class)
                )
        );
    }

    @Test
    void givenNoTransformer_whenBeforeBodyWrite_thenReturnOriginal() {
        DummyDto input = new DummyDto("unchanged");

        when(transformerRegistry.getTransformer(DummyDto.class)).thenReturn(Optional.empty());

        Object result = advice.beforeBodyWrite(
                input, null, MediaType.APPLICATION_JSON, StringHttpMessageConverter.class,
                mock(ServerHttpRequest.class), mock(ServerHttpResponse.class)
        );

        assertSame(input, result);
    }

    @Test
    void givenNullInput_whenBeforeBodyWrite_thenReturnOriginal() {
        Object result = advice.beforeBodyWrite(
                null, null, MediaType.APPLICATION_JSON, StringHttpMessageConverter.class,
                mock(ServerHttpRequest.class), mock(ServerHttpResponse.class)
        );

        assertNull(result);
    }

    @Test
    void givenCollectionOfTransformable_whenSupports_thenReturnTrue() throws NoSuchMethodException {
        Method method = SampleCollectionController.class.getMethod("getDummyDtoList");
        MethodParameter parameter = new MethodParameter(method, -1);

        when(transformerRegistry.hasTransformer(DummyDto.class)).thenReturn(true);

        boolean result = advice.supports(parameter, StringHttpMessageConverter.class);

        assertTrue(result);
    }

    @Test
    void givenCollectionOfNonTransformable_whenSupports_thenReturnFalse() throws NoSuchMethodException {
        Method method = SampleCollectionController.class.getMethod("getStringList");
        MethodParameter parameter = new MethodParameter(method, -1);

        when(transformerRegistry.hasTransformer(String.class)).thenReturn(false);

        boolean result = advice.supports(parameter, StringHttpMessageConverter.class);

        assertFalse(result);
    }

    // Simple dummy DTO
    static class DummyDto {
        String value;

        DummyDto(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DummyDto other)) return false;
            return value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    // Dummy controller to simulate MethodParameter targets
    static class SampleController {
        public DummyDto getDto() {
            return null;
        }

        public String getString() {
            return "";
        }
    }

    // New dummy controller for collection return types
    static class SampleCollectionController {
        public List<DummyDto> getDummyDtoList() {
            return List.of();
        }

        public List<String> getStringList() {
            return List.of();
        }
    }
}