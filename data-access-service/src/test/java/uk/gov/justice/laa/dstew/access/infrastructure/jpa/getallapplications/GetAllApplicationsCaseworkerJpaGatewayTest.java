package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallapplications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;

@ExtendWith(MockitoExtension.class)
class GetAllApplicationsCaseworkerJpaGatewayTest {

  @Mock private CaseworkerRepository caseworkerRepository;

  private GetAllApplicationsCaseworkerJpaGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new GetAllApplicationsCaseworkerJpaGateway(caseworkerRepository);
  }

  @Test
  void givenCaseworkerExists_whenCaseworkerExists_thenReturnsTrue() {
    UUID userId = UUID.randomUUID();
    when(caseworkerRepository.countById(userId)).thenReturn(1L);

    assertThat(gateway.caseworkerExists(userId)).isTrue();
  }

  @Test
  void givenCaseworkerDoesNotExist_whenCaseworkerExists_thenReturnsFalse() {
    UUID userId = UUID.randomUUID();
    when(caseworkerRepository.countById(userId)).thenReturn(0L);

    assertThat(gateway.caseworkerExists(userId)).isFalse();
  }
}
