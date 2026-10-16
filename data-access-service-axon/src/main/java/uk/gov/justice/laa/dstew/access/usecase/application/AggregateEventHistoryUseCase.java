package uk.gov.justice.laa.dstew.access.usecase.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationCurrentStateAccessPolicy;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadAccessPolicyProvider;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.history.AggregateEventHistoryReader;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityCurrentStateAccessPolicy;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadRepository;
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Authorizes aggregate event reads using delegated scopes and projected ownership. */
@Service
@RequiredArgsConstructor
public class AggregateEventHistoryUseCase {

  private final AggregateEventHistoryReader reader;
  private final AxonConfiguration configuration;
  private final ApplicationReadAccessPolicyProvider scopeProvider;
  private final ApplicationReadRepository applications;
  private final ApplicationCurrentStateAccessPolicy applicationPolicy;
  private final PriorAuthorityReadRepository priorAuthorities;
  private final PriorAuthorityCurrentStateAccessPolicy priorAuthorityPolicy;

  /** Returns the authorized application's complete thin event history. */
  @AllowApiCaseworker
  public List<EventMessage> applicationEvents(UUID id) {
    var scope = scopeProvider.resolve();
    if (scope instanceof OfficeCodeReadAccessScope
        && !applications.exists(
            applicationPolicy
                .restrictionFor(scope)
                .and((root, query, builder) -> builder.equal(root.get("applicationId"), id)))) {
      throw notFound(id);
    }
    return read("ApplicationAggregate", id);
  }

  /** Returns the authorized prior authority's complete thin event history. */
  @AllowApiCaseworker
  public List<EventMessage> priorAuthorityEvents(UUID id) {
    var scope = scopeProvider.resolve();
    if (scope instanceof OfficeCodeReadAccessScope
        && !priorAuthorities.exists(
            priorAuthorityPolicy
                .restrictionFor(scope)
                .and((root, query, builder) -> builder.equal(root.get("priorAuthorityId"), id)))) {
      throw notFound(id);
    }
    return read("PriorAuthorityAggregate", id);
  }

  private List<EventMessage> read(String aggregateType, UUID id) {
    List<EventMessage> events =
        configuration
            .getComponent(UnitOfWorkFactory.class)
            .create()
            .executeWithResult(
                context ->
                    CompletableFuture.completedFuture(reader.read(aggregateType, id, context)))
            .join();
    if (events.isEmpty()) {
      throw notFound(id);
    }
    return events;
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("No accessible resource found with ID: " + id);
  }
}
