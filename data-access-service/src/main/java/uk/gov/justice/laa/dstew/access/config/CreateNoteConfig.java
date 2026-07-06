package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.controller.application.CreateNoteCommandMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.createnote.NoteJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.createnote.CreateNoteUseCase;
import uk.gov.justice.laa.dstew.access.usecase.createnote.infrastructure.NoteGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;

/** Spring configuration that wires all beans for the createNote use case. */
@Configuration
@RequiredArgsConstructor
public class CreateNoteConfig {

  private final NoteRepository noteRepository;
  private final SaveDomainEventService saveDomainEventService;
  private final ObjectMapper objectMapper;
  private final ApplicationGateway applicationGateway;

  /** Creates the {@link NoteJpaGateway} bean. */
  @Bean
  public NoteJpaGateway createNoteNoteJpaGateway() {
    return new NoteJpaGateway(noteRepository);
  }

  /**
   * Creates the {@link CreateNoteUseCase} bean.
   *
   * @param noteGateway the note persistence gateway
   * @return a fully configured use case
   */
  @Bean
  public CreateNoteUseCase createNoteUseCase(NoteGateway noteGateway) {
    return new CreateNoteUseCase(applicationGateway, noteGateway, saveDomainEventService);
  }

  /** Creates the {@link CreateNoteCommandMapper} bean. */
  @Bean
  public CreateNoteCommandMapper createNoteCommandMapper() {
    return new CreateNoteCommandMapper(objectMapper);
  }
}
