package uk.gov.justice.laa.dstew.access.transformation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.GenericTypeResolver;
import org.springframework.stereotype.Component;

/**
 * Registry to find a ResponseTransformer instance for a DTO class.
 */
@Component
class TransformerRegistry {
  private final Map<Class<?>, ResponseTransformer<?>> transformerByDtoClass = new HashMap<>();

  TransformerRegistry(final List<ResponseTransformer<?>> transformerList) {
    for (final var transformer : transformerList) {
      final var dtoClass = GenericTypeResolver.resolveTypeArgument(transformer.getClass(), ResponseTransformer.class);
      transformerByDtoClass.put(dtoClass, transformer);
    }
  }

  boolean hasTransformer(final Class<?> dtoClass) {
    return transformerByDtoClass.containsKey(dtoClass);
  }

  @SuppressWarnings("unchecked")
  <T> Optional<ResponseTransformer<T>> getTransformer(final Class<T> dtoClass) {
    return Optional.ofNullable((ResponseTransformer<T>) transformerByDtoClass.get(dtoClass));
  }
}
