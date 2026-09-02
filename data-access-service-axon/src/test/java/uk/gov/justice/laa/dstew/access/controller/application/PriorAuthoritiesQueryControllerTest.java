package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.GetPriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.GetPriorAuthorityUseCase;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.model.PriorAuthorityResult;

/** Verifies that each controller endpoint delegates to the appropriate use case and mapper. */
class PriorAuthoritiesQueryControllerTest {

  private GetPriorAuthorityUseCase getPriorAuthorityUseCase;
  private GetPriorAuthorityDraftUseCase getPriorAuthorityDraftUseCase;
  private GetPriorAuthorityResponseMapper getPriorAuthorityResponseMapper;
  private PriorAuthoritiesQueryController controller;

  @BeforeEach
  void setUp() {
    getPriorAuthorityUseCase = mock(GetPriorAuthorityUseCase.class);
    getPriorAuthorityDraftUseCase = mock(GetPriorAuthorityDraftUseCase.class);
    getPriorAuthorityResponseMapper = mock(GetPriorAuthorityResponseMapper.class);
    controller =
        new PriorAuthoritiesQueryController(
            getPriorAuthorityUseCase,
            getPriorAuthorityDraftUseCase,
            getPriorAuthorityResponseMapper);
  }

  @Test
  void givenExistingPriorAuthority_whenGetPriorAuthority_thenDelegatesToUseCaseAndMapper() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityResult result = mock(PriorAuthorityResult.class);
    PriorAuthorityResponse response = new PriorAuthorityResponse();
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)).thenReturn(result);
    when(getPriorAuthorityResponseMapper.toResponse(result)).thenReturn(response);

    ResponseEntity<PriorAuthorityResponse> actual =
        controller.getPriorAuthority(null, priorAuthorityId);

    assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(actual.getBody()).isSameAs(response);
    verify(getPriorAuthorityUseCase).getPriorAuthority(priorAuthorityId);
  }

  @Test
  void givenExistingDraft_whenGetPriorAuthorityDraft_thenDelegatesToUseCaseAndMapper() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityResult result = mock(PriorAuthorityResult.class);
    PriorAuthorityResponse response = new PriorAuthorityResponse();
    when(getPriorAuthorityDraftUseCase.getPriorAuthorityDraft(priorAuthorityId)).thenReturn(result);
    when(getPriorAuthorityResponseMapper.toResponse(result)).thenReturn(response);

    ResponseEntity<PriorAuthorityResponse> actual =
        controller.getPriorAuthorityDraft(null, priorAuthorityId);

    assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(actual.getBody()).isSameAs(response);
    verify(getPriorAuthorityDraftUseCase).getPriorAuthorityDraft(priorAuthorityId);
  }
}
