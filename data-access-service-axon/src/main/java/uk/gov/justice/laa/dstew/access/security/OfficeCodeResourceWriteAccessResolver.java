package uk.gov.justice.laa.dstew.access.security;

import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves an office-code target from a {@link WriteAccessResolverResource} handler parameter. */
@Component
public class OfficeCodeResourceWriteAccessResolver implements WriteAccessTargetResolver {

  @Override
  public List<ProtectedResource> resolve(Object resource) {
    if (!(resource instanceof OfficeCodeOwned owned)) {
      throw new IllegalStateException(
          "@WriteAccessResolverResource parameter must implement OfficeCodeOwned");
    }
    return List.of(new ProtectedResource(owned.getOfficeCode()));
  }
}
