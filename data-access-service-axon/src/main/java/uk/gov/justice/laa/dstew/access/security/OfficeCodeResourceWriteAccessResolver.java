package uk.gov.justice.laa.dstew.access.security;

import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves an office-code target from an {@link AggregateResource} handler parameter. */
@Component
public class OfficeCodeResourceWriteAccessResolver implements WriteAccessTargetResolver {

  @Override
  public boolean requiresHandlerResource() {
    return true;
  }

  @Override
  public List<ProtectedResource> resolve(Object command, Object resource) {
    if (!(resource instanceof OfficeCodeOwned owned)) {
      throw new IllegalStateException(
          "@AggregateResource parameter must implement OfficeCodeOwned");
    }
    return List.of(new ProtectedResource(owned.getOfficeCode()));
  }
}
