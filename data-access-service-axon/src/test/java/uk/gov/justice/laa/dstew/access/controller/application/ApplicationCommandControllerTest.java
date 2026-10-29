package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionUseCase;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ExpectedLinkedGroup;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkApplicationUseCase;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkType;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MakeApplicationLeadCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MakeApplicationLeadUseCase;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.UnlinkApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.UnlinkApplicationUseCase;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteUseCase;
import uk.gov.justice.laa.dstew.access.command.application.update.UpdateApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.update.UpdateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;
import uk.gov.justice.laa.dstew.access.model.LinkedGroupChangeRequest;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCallerFactory;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedUserId;
import uk.gov.justice.laa.dstew.access.version.VersionToken;

/** Verifies that each controller endpoint delegates to the appropriate use case. */
@ExtendWith(MockitoExtension.class)
class ApplicationCommandControllerTest {

  private CreateApplicationUseCase createApplicationUseCase;
  private MakeApplicationDecisionUseCase makeDecisionUseCase;
  private CreateNoteUseCase createNoteUseCase;
  private UpdateApplicationUseCase updateApplicationUseCase;
  private LinkApplicationUseCase linkApplicationUseCase;
  @Mock private MakeApplicationLeadUseCase makeApplicationLeadUseCase;
  @Mock private UnlinkApplicationUseCase unlinkApplicationUseCase;
  private CreateApplicationCommandMapper commandMapper;
  private MakeDecisionCommandMapper decisionCommandMapper;
  private CreateNoteCommandMapper createNoteCommandMapper;
  private UpdateApplicationCommandMapper updateApplicationCommandMapper;
  private LinkApplicationCommandMapper linkApplicationCommandMapper;
  @Mock private MakeApplicationLeadCommandMapper makeApplicationLeadCommandMapper;
  @Mock private UnlinkApplicationCommandMapper unlinkApplicationCommandMapper;
  @Mock private AuthenticatedCallerFactory authenticatedCallerFactory;
  private AuthenticatedUserId authenticatedUserId;
  private ApplicationCommandController controller;

  @BeforeEach
  void setUp() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    createApplicationUseCase = mock(CreateApplicationUseCase.class);
    makeDecisionUseCase = mock(MakeApplicationDecisionUseCase.class);
    createNoteUseCase = mock(CreateNoteUseCase.class);
    updateApplicationUseCase = mock(UpdateApplicationUseCase.class);
    linkApplicationUseCase = mock(LinkApplicationUseCase.class);
    commandMapper = mock(CreateApplicationCommandMapper.class);
    decisionCommandMapper = mock(MakeDecisionCommandMapper.class);
    createNoteCommandMapper = mock(CreateNoteCommandMapper.class);
    updateApplicationCommandMapper = mock(UpdateApplicationCommandMapper.class);
    linkApplicationCommandMapper = mock(LinkApplicationCommandMapper.class);
    authenticatedUserId = mock(AuthenticatedUserId.class);
    controller =
        new ApplicationCommandController(
            createApplicationUseCase,
            makeDecisionUseCase,
            createNoteUseCase,
            updateApplicationUseCase,
            linkApplicationUseCase,
            makeApplicationLeadUseCase,
            unlinkApplicationUseCase,
            commandMapper,
            decisionCommandMapper,
            createNoteCommandMapper,
            updateApplicationCommandMapper,
            linkApplicationCommandMapper,
            makeApplicationLeadCommandMapper,
            unlinkApplicationCommandMapper,
            authenticatedCallerFactory,
            authenticatedUserId);
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void givenRequest_whenLinkApplication_thenDelegatesToUseCaseAndReturnsNoContent() {
    UUID id = UUID.randomUUID();
    ApplicationLinkRequest request =
        new ApplicationLinkRequest(UUID.randomUUID(), ApplicationLinkType.FAMILY);
    AuthenticatedCaller caller = mock(AuthenticatedCaller.class);
    var command =
        new LinkApplicationCommand(
            id, request.getApplicationId(), LinkType.FAMILY, null, Instant.now(), caller);
    when(authenticatedCallerFactory.currentCaller()).thenReturn(caller);
    when(linkApplicationCommandMapper.toCommand(id, request, caller)).thenReturn(command);

    ResponseEntity<Void> response = controller.linkApplication(null, id, request);

    verify(linkApplicationUseCase).execute(command);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void givenApplication_whenMakeApplicationLead_thenDelegatesAndReturnsNoContent() {
    UUID id = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    LinkedGroupChangeRequest request =
        new LinkedGroupChangeRequest(VersionToken.linkedGroup(groupId, 3L).encode());
    AuthenticatedCaller caller = mock(AuthenticatedCaller.class);
    MakeApplicationLeadCommand command =
        new MakeApplicationLeadCommand(
            id, new ExpectedLinkedGroup(groupId, 3L), Instant.now(), caller);
    when(authenticatedCallerFactory.currentCaller()).thenReturn(caller);
    when(makeApplicationLeadCommandMapper.toCommand(id, request, caller)).thenReturn(command);

    ResponseEntity<Void> response = controller.makeApplicationLead(null, id, request);

    verify(makeApplicationLeadUseCase).execute(command);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void givenApplication_whenUnlinkApplication_thenDelegatesAndReturnsNoContent() {
    UUID id = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    LinkedGroupChangeRequest request =
        new LinkedGroupChangeRequest(VersionToken.linkedGroup(groupId, 3L).encode());
    AuthenticatedCaller caller = mock(AuthenticatedCaller.class);
    UnlinkApplicationCommand command =
        new UnlinkApplicationCommand(
            id, new ExpectedLinkedGroup(groupId, 3L), Instant.now(), caller);
    when(authenticatedCallerFactory.currentCaller()).thenReturn(caller);
    when(unlinkApplicationCommandMapper.toCommand(id, request, caller)).thenReturn(command);

    ResponseEntity<Void> response = controller.unlinkApplication(null, id, request);

    verify(unlinkApplicationUseCase).execute(command);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void givenRequest_whenUpdateApplication_thenDelegatesToUseCase() {
    UUID id = UUID.randomUUID();
    UpdateApplicationCommand command = mock(UpdateApplicationCommand.class);
    when(updateApplicationCommandMapper.toCommand(id, null)).thenReturn(command);
    controller.updateApplication(null, id, null);
    verify(updateApplicationUseCase).execute(command);
  }

  @Test
  void givenProjectedResult_whenCreateApplication_thenDelegatesToUseCaseAndReturns201() {
    CreateApplicationCommand command = stubCreateCommand();
    when(commandMapper.toCommand(any(), anyInt())).thenReturn(command);
    when(createApplicationUseCase.execute(command)).thenReturn(true);
    ResponseEntity<Void> response = controller.createApplication(null, null, 1);
    verify(createApplicationUseCase).execute(command);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void givenTimeoutResult_whenCreateApplication_thenDelegatesToUseCaseAndReturns202() {
    CreateApplicationCommand command = stubCreateCommand();
    when(commandMapper.toCommand(any(), anyInt())).thenReturn(command);
    when(createApplicationUseCase.execute(command)).thenReturn(false);
    ResponseEntity<Void> response = controller.createApplication(null, null, 1);
    verify(createApplicationUseCase).execute(command);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void givenRequest_whenMakeDecision_thenDelegatesToUseCase() {
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    MakeApplicationDecisionCommand command = mock(MakeApplicationDecisionCommand.class);
    when(authenticatedUserId.get()).thenReturn(caseworkerId);
    when(decisionCommandMapper.toCommand(id, caseworkerId, null)).thenReturn(command);
    controller.makeDecision(null, id, null);
    verify(makeDecisionUseCase).execute(command);
  }

  @Test
  void givenRequest_whenCreateNote_thenDelegatesToUseCase() {
    UUID id = UUID.randomUUID();
    CreateNoteCommand command = mock(CreateNoteCommand.class);
    when(createNoteCommandMapper.toCommand(id, null)).thenReturn(command);
    controller.createApplicationNotes(null, id, null);
    verify(createNoteUseCase).execute(command);
  }

  private CreateApplicationCommand stubCreateCommand() {
    UUID id = UUID.randomUUID();
    return new CreateApplicationCommand(
        id,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        Map.of("id", id.toString()),
        "{}",
        1,
        "BaseCivilApplication.json",
        null);
  }
}
