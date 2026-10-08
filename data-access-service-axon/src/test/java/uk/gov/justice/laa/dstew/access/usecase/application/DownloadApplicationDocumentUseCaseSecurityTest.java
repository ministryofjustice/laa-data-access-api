package uk.gov.justice.laa.dstew.access.usecase.application;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.utils.BaseSecuredUseCaseTest;
import uk.gov.justice.laa.dstew.access.utils.TestSecurityConfig;

@SpringBootTest(classes = {DownloadApplicationDocumentUseCase.class, TestSecurityConfig.class})
@ImportAutoConfiguration(
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
class DownloadApplicationDocumentUseCaseSecurityTest extends BaseSecuredUseCaseTest {

  @Autowired private DownloadApplicationDocumentUseCase useCase;

  @MockitoBean private QueryGateway queryGateway;
  @MockitoBean private SdsService sdsService;

  @Test
  void givenNoRole_whenDownloaded_thenDeniesAccessWithoutReadingDocument() {
    setSecurityContext(NO_ROLE);

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> useCase.downloadDocument(UUID.randomUUID(), UUID.randomUUID()))
        .withMessageContaining("Access Denied");

    verifyNoInteractions(queryGateway, sdsService);
  }
}
