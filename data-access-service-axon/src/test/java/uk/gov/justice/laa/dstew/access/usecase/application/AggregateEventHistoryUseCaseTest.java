package uk.gov.justice.laa.dstew.access.usecase.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationCurrentStateAccessPolicy;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadAccessPolicyProvider;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.history.AggregateEventHistoryReader;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityCurrentStateAccessPolicy;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadRepository;
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.UnrestrictedReadAccessScope;

class AggregateEventHistoryUseCaseTest {

  private final UUID id = UUID.randomUUID();
  private final AxonConfiguration configuration = mock(AxonConfiguration.class);
  private final ProcessingContext context = mock(ProcessingContext.class);
  private final AggregateEventHistoryReader reader = mock(AggregateEventHistoryReader.class);
  private final ApplicationReadAccessPolicyProvider scopes =
      mock(ApplicationReadAccessPolicyProvider.class);
  private final ApplicationReadRepository applications = mock(ApplicationReadRepository.class);
  private final PriorAuthorityReadRepository priorAuthorities =
      mock(PriorAuthorityReadRepository.class);
  private final AggregateEventHistoryUseCase useCase =
      new AggregateEventHistoryUseCase(
          reader,
          configuration,
          scopes,
          applications,
          new ApplicationCurrentStateAccessPolicy(),
          priorAuthorities,
          new PriorAuthorityCurrentStateAccessPolicy());

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void providerCannotReadBeforeOwnershipProjectionIsVisible(boolean application) {
    when(scopes.resolve()).thenReturn(new OfficeCodeReadAccessScope(Set.of("0Z123A")));

    assertThatThrownBy(() -> read(application)).isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(reader);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void unsupportedDelegatedScopeCannotReadEvents(boolean application) {
    when(scopes.resolve()).thenThrow(new AccessDeniedException("unsupported scope"));

    assertThatThrownBy(() -> read(application)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(reader, applications, priorAuthorities);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void providerCanReadWhenOwnershipProjectionIsVisible(boolean application) {
    when(scopes.resolve()).thenReturn(new OfficeCodeReadAccessScope(Set.of("0Z123A")));
    if (application) {
      when(applications.exists(
              org.mockito.ArgumentMatchers.<Specification<ApplicationReadModel>>any()))
          .thenReturn(true);
    } else {
      when(priorAuthorities.exists(
              org.mockito.ArgumentMatchers.<Specification<PriorAuthorityReadModel>>any()))
          .thenReturn(true);
    }
    List<EventMessage> events = List.of(mock(EventMessage.class));
    stubHistoricalRead(application, events);

    assertThat(read(application)).containsExactlyElementsOf(events);
    verify(reader).read(aggregateType(application), id, context);
    if (application) {
      verify(applications)
          .exists(org.mockito.ArgumentMatchers.<Specification<ApplicationReadModel>>any());
      verifyNoInteractions(priorAuthorities);
    } else {
      verify(priorAuthorities)
          .exists(org.mockito.ArgumentMatchers.<Specification<PriorAuthorityReadModel>>any());
      verifyNoInteractions(applications);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void unrestrictedCallerCanReadWithoutOwnershipProjection(boolean application) {
    when(scopes.resolve()).thenReturn(new UnrestrictedReadAccessScope());
    List<EventMessage> events = List.of(mock(EventMessage.class));
    stubHistoricalRead(application, events);

    assertThat(read(application)).containsExactlyElementsOf(events);
    verifyNoInteractions(applications, priorAuthorities);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void emptyHistoricalStreamReturnsNotFound(boolean application) {
    when(scopes.resolve()).thenReturn(new UnrestrictedReadAccessScope());
    stubHistoricalRead(application, List.of());

    assertThatThrownBy(() -> read(application)).isInstanceOf(ResourceNotFoundException.class);
    verify(reader).read(aggregateType(application), id, context);
  }

  private void stubHistoricalRead(boolean application, List<EventMessage> events) {
    UnitOfWorkFactory factory = mock(UnitOfWorkFactory.class);
    UnitOfWork unitOfWork = mock(UnitOfWork.class);
    when(configuration.getComponent(UnitOfWorkFactory.class)).thenReturn(factory);
    when(factory.create()).thenReturn(unitOfWork);
    when(unitOfWork.executeWithResult(any()))
        .thenAnswer(
            invocation -> {
              Function<ProcessingContext, CompletableFuture<List<EventMessage>>> action =
                  invocation.getArgument(0);
              return action.apply(context);
            });
    when(reader.read(aggregateType(application), id, context)).thenReturn(events);
  }

  private String aggregateType(boolean application) {
    return application ? "ApplicationAggregate" : "PriorAuthorityAggregate";
  }

  private List<EventMessage> read(boolean application) {
    if (application) {
      return useCase.applicationEvents(id);
    }
    return useCase.priorAuthorityEvents(id);
  }
}
