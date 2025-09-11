package uk.gov.justice.laa.dstew.access.transformation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Intercept controller responses and apply cross-cutting transformations,
 * such as redaction or anonymization (for GDPR or any other reason).
 */
@RequiredArgsConstructor
@RestControllerAdvice
class TransformationAdvice implements ResponseBodyAdvice<Object> {
  private final TransformerRegistry transformerRegistry;

  @Override
  public boolean supports(final MethodParameter returnType,
                          final Class<? extends HttpMessageConverter<?>> converterType) {
    return transformerRegistry.hasTransformer(returnType.getParameterType()) || supportsCollection(returnType);
  }

  private boolean supportsCollection(final MethodParameter returnType) {
    final var resolvable = ResolvableType.forMethodParameter(returnType);
    if (!Collection.class.isAssignableFrom(resolvable.toClass())) {
      return false;
    }
    final var elementType = resolvable.getGeneric(0).resolve();
    return elementType != null && transformerRegistry.hasTransformer(elementType);
  }

  @Override
  public Object beforeBodyWrite(final Object responseDto, final MethodParameter returnType,
                                final MediaType contentType, final Class<? extends HttpMessageConverter<?>> converterType,
                                final ServerHttpRequest request, final ServerHttpResponse response) {
    if (responseDto != null) {
      if (responseDto instanceof List<?> listDto) {
        if (!listDto.isEmpty()) {
          final var maybeTransformer = transformerRegistry.getTransformer(listDto.getFirst().getClass());
          if (maybeTransformer.isPresent()) {
            @SuppressWarnings("unchecked") final var transformer = (ResponseTransformer<Object>) maybeTransformer.get();
            return listDto.stream()
                .map(transformer::transform)
                .filter(Objects::nonNull)
                .toList();
          }
        }
      } else {
        final var maybeTransformer = transformerRegistry.getTransformer(responseDto.getClass());
        if (maybeTransformer.isPresent()) {
          @SuppressWarnings("unchecked") final var transformer = (ResponseTransformer<Object>) maybeTransformer.get();
          final var transformed = transformer.transform(responseDto);
          if (transformed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
          }
          return transformed;
        }
      }
    }
    // If no transformer was invoked, then the controller method can still return `null` (or a list
    // containing `null` elements) without throwing a `NOT_FOUND` exception (or filtering them out).
    return responseDto;
  }
}
