package uk.gov.justice.laa.dstew.access.security;

import java.util.List;
import org.springframework.stereotype.Component;

/** Applies the provider-office write policy to office-code access targets. */
@Component
public class OfficeCodeWriteAccessAuthorizer
    implements WriteAccessAuthorizer<OfficeCodeWriteAccessTarget> {
  private final OfficeCodeWriteAccessPolicy policy;

  public OfficeCodeWriteAccessAuthorizer(OfficeCodeWriteAccessPolicy policy) {
    this.policy = policy;
  }

  @Override
  public Class<OfficeCodeWriteAccessTarget> targetType() {
    return OfficeCodeWriteAccessTarget.class;
  }

  @Override
  public void requireWriteAccess(
      List<OfficeCodeWriteAccessTarget> targets, AuthenticatedCaller caller) {
    targets.forEach(target -> policy.requireWriteAccess(target.officeCode(), caller));
  }
}
