package uk.gov.justice.laa.dstew.access.query.caseworker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.caseworker.Caseworker;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;

class CaseworkersQueryHandlerTest {

  private CaseworkerRepository caseworkerRepository;
  private CaseworkersQueryHandler handler;

  @BeforeEach
  void setUp() {
    caseworkerRepository = mock(CaseworkerRepository.class);
    handler = new CaseworkersQueryHandler(caseworkerRepository);
  }

  @Test
  void givenCaseworkers_whenHandled_thenReturnsCaseworkers() {
    Caseworker first = new Caseworker(UUID.randomUUID(), "alice@example.com");
    Caseworker second = new Caseworker(UUID.randomUUID(), "bob@example.com");
    when(caseworkerRepository.findAll()).thenReturn(List.of(first, second));

    FindCaseworkersResult result = handler.handle(new FindCaseworkersQuery());

    assertThat(result.caseworkers()).containsExactly(first, second);
    verify(caseworkerRepository).findAll();
  }

  @Test
  void givenNoCaseworkers_whenHandled_thenReturnsEmptyList() {
    when(caseworkerRepository.findAll()).thenReturn(List.of());

    FindCaseworkersResult result = handler.handle(new FindCaseworkersQuery());

    assertThat(result.caseworkers()).isEmpty();
  }
}
