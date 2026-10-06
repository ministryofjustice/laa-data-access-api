package uk.gov.justice.laa.dstew.access.usecase.individuals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadAccessPolicyProvider;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsQuery;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsResult;
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.UnrestrictedReadAccessScope;
import uk.gov.justice.laa.dstew.access.utils.BaseSecuredUseCaseTest;
import uk.gov.justice.laa.dstew.access.utils.TestSecurityConfig;

@SpringBootTest(classes = {GetAllIndividualsUseCase.class, TestSecurityConfig.class})
@ImportAutoConfiguration(
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
class GetAllIndividualsUseCaseSecurityTest extends BaseSecuredUseCaseTest {

  @Autowired private GetAllIndividualsUseCase useCase;

  @MockitoBean private QueryGateway queryGateway;
  @MockitoBean private ApplicationReadAccessPolicyProvider accessPolicyProvider;

  @Test
  void givenNoRole_whenExecute_thenThrowsAuthorizationDeniedException() {
    setSecurityContext(NO_ROLE);

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> useCase.execute(new FindIndividualsQuery(null, null, false, 1, 20)))
        .withMessageContaining("Access Denied");

    verifyNoInteractions(queryGateway);
  }

  @Test
  void givenBlankAuthenticatedName_whenExecute_thenThrowsAuthorizationDeniedException() {
    setSecurityContextWithName(" ", CASEWORKER_ROLE);

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> useCase.execute(new FindIndividualsQuery(null, null, false, 1, 20)))
        .withMessageContaining("Access Denied");

    verifyNoInteractions(queryGateway);
  }

  @Test
  void givenCaseworkerRoleAndName_whenExecute_thenDelegatesToQueryGateway() {
    setSecurityContext(CASEWORKER_ROLE);
    FindIndividualsResult expected =
        new FindIndividualsResult(new ApplicationClient(), 1, 20, 0, false);
    when(accessPolicyProvider.resolve()).thenReturn(new UnrestrictedReadAccessScope());
    when(queryGateway.query(any(FindIndividualsQuery.class), eq(FindIndividualsResult.class)))
        .thenReturn(CompletableFuture.completedFuture(expected));

    FindIndividualsResult result =
        useCase.execute(new FindIndividualsQuery(null, null, false, 1, 20));

    assertThat(result).isSameAs(expected);
  }

  @Test
  void givenProviderOfficeScope_whenExecute_thenDispatchesScopedQuery() {
    setSecurityContext(CASEWORKER_ROLE);
    OfficeCodeReadAccessScope scope = new OfficeCodeReadAccessScope(java.util.Set.of("OFFICE-1"));
    FindIndividualsResult expected =
        new FindIndividualsResult(new ApplicationClient(), 1, 20, 0, false);
    when(accessPolicyProvider.resolve()).thenReturn(scope);
    when(queryGateway.query(any(FindIndividualsQuery.class), eq(FindIndividualsResult.class)))
        .thenReturn(CompletableFuture.completedFuture(expected));

    useCase.execute(new FindIndividualsQuery(null, null, false, 1, 20));

    ArgumentCaptor<FindIndividualsQuery> queryCaptor =
        ArgumentCaptor.forClass(FindIndividualsQuery.class);
    verify(queryGateway).query(queryCaptor.capture(), eq(FindIndividualsResult.class));
    assertThat(queryCaptor.getValue().accessScope()).isEqualTo(scope);
  }
}
