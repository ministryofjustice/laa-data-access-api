package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.applications.SdsService;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.AssignCaseworkerUseCase;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.createnote.CreateNoteUseCase;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionUseCase;
import uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker.UnassignCaseworkerUseCase;
import uk.gov.justice.laa.dstew.access.usecase.updateapplication.UpdateApplicationUseCase;

class ApplicationCommandControllerCreatePriorAuthorityTest {

  private final CreateApplicationUseCase createApplicationUseCase =
      mock(CreateApplicationUseCase.class);
  private final CreateApplicationCommandMapper createApplicationCommandMapper =
      mock(CreateApplicationCommandMapper.class);
  private final UpdateApplicationUseCase updateApplicationUseCase =
      mock(UpdateApplicationUseCase.class);
  private final UpdateApplicationCommandMapper updateApplicationCommandMapper =
      mock(UpdateApplicationCommandMapper.class);
  private final AssignCaseworkerUseCase assignCaseworkerUseCase =
      mock(AssignCaseworkerUseCase.class);
  private final AssignCaseworkerCommandMapper assignCaseworkerCommandMapper =
      mock(AssignCaseworkerCommandMapper.class);
  private final UnassignCaseworkerUseCase unassignCaseworkerUseCase =
      mock(UnassignCaseworkerUseCase.class);
  private final UnassignCaseworkerCommandMapper unassignCaseworkerCommandMapper =
      mock(UnassignCaseworkerCommandMapper.class);
  private final MakeDecisionUseCase makeDecisionUseCase = mock(MakeDecisionUseCase.class);
  private final MakeDecisionCommandMapper makeDecisionCommandMapper =
      mock(MakeDecisionCommandMapper.class);
  private final CreateNoteUseCase createNoteUseCase = mock(CreateNoteUseCase.class);
  private final CreateNoteCommandMapper createNoteCommandMapper =
      mock(CreateNoteCommandMapper.class);
  private final SdsService sdsService = mock(SdsService.class);

  private final ApplicationCommandController controller =
      new ApplicationCommandController(
          createApplicationUseCase,
          createApplicationCommandMapper,
          updateApplicationUseCase,
          updateApplicationCommandMapper,
          assignCaseworkerUseCase,
          assignCaseworkerCommandMapper,
          unassignCaseworkerUseCase,
          unassignCaseworkerCommandMapper,
          makeDecisionUseCase,
          makeDecisionCommandMapper,
          createNoteUseCase,
          createNoteCommandMapper,
          sdsService);

  @Test
  void createPriorAuthority_returns501NotImplemented_withNoUseCaseInteraction() {
    ResponseEntity<CreatePriorAuthorityResponse> response =
        controller.createPriorAuthority(
            ServiceName.CIVIL_APPLY, UUID.randomUUID(), new CreatePriorAuthorityRequest());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
    verifyNoInteractions(
        createApplicationUseCase,
        updateApplicationUseCase,
        assignCaseworkerUseCase,
        unassignCaseworkerUseCase,
        makeDecisionUseCase,
        createNoteUseCase,
        sdsService);
  }
}
