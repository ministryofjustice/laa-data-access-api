package uk.gov.justice.laa.dstew.access.query.application;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.ReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.UnrestrictedReadAccessScope;
import uk.gov.justice.laa.dstew.access.security.SecurityHelper;

/** Resolves Application row visibility from authenticated request data. */
@Component
public class ApplicationReadAccessPolicyProvider {
  private final ServiceNameContext serviceNameContext;

  public ApplicationReadAccessPolicyProvider(ServiceNameContext serviceNameContext) {
    this.serviceNameContext = serviceNameContext;
  }

  public ReadAccessScope resolve() {
    if (serviceNameContext.getServiceName() != ServiceName.CIVIL_MANAGE) {
      return new UnrestrictedReadAccessScope();
    }
    Set<String> officeCodes = new LinkedHashSet<>();
    SecurityHelper.getLaaAccounts().stream()
        .filter(account -> account != null && !account.isBlank())
        .map(String::trim)
        .forEach(officeCodes::add);
    return new OfficeCodeReadAccessScope(officeCodes);
  }
}

