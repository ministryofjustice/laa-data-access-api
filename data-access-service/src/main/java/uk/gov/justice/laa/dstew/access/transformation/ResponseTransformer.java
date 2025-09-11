package uk.gov.justice.laa.dstew.access.transformation;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

/**
 * If a response transformer uses app roles, it can request an instance of `EffectiveAuthorizationProvider` to be
 * injected from the Spring application context.
 *
 * @param <T> The response type.
 */
interface ResponseTransformer<T> {
  /**
   * Transform a response of type `T` (without changing its type).
   * Throwing a `RuntimeException` will prevent any response from being sent.
   * Instead, transform to `null` to remove the item from the response.
   *
   * @param response Untransformed response object.
   * @return Possibly-transformed response object. Return `null` to respond
   *         with a 404 error response (GET of an individual item) or to omit
   *         an item from the response List (GET of a resource collection).
   */
  T transform(T response);
}
