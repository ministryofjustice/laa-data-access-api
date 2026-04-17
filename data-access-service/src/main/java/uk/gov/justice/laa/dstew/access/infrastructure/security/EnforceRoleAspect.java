package uk.gov.justice.laa.dstew.access.infrastructure.security;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.AccessPolicy;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.EnforceRole;

/** AOP aspect that enforces @EnforceRole annotations by delegating to AccessPolicy. */
@Aspect
@Component
public class EnforceRoleAspect {

  private final AccessPolicy accessPolicy;

  public EnforceRoleAspect(AccessPolicy accessPolicy) {
    this.accessPolicy = accessPolicy;
  }

  @Before("@annotation(enforceRole)")
  public void enforce(EnforceRole enforceRole) {
    accessPolicy.enforce(enforceRole);
  }
}
