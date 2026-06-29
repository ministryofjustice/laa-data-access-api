package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.controller.application.CreateNoteCommandMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.createnote.CreateNoteNoteJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.createnote.CreateNoteUseCase;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;

/** Spring configuration that wires all beans for the createNote use case. */
@Configuration
@RequiredArgsConstructor
public class CreateNoteConfig {

  private final NoteRepository noteRepository;
  private final SaveDomainEventService saveDomainEventService;
  private final ObjectMapper objectMapper;
  private final ApplicationGateway applicationGateway;

  /** Creates the {@link CreateNoteNoteJpaGateway} bean. */
  @Bean
  public CreateNoteNoteJpaGateway createNoteNoteJpaGateway() {
    return new CreateNoteNoteJpaGateway(noteRepository);
  }

  /**
   * Creates the {@link CreateNoteUseCase} bean.
   *
   * @param noteJpaGateway the note persistence gateway
   * @return a fully configured use case
   */
  @Bean
  public CreateNoteUseCase createNoteUseCase(CreateNoteNoteJpaGateway noteJpaGateway) {
    return new CreateNoteUseCase(applicationGateway, noteJpaGateway, saveDomainEventService);
  }

  /** Creates the {@link CreateNoteCommandMapper} bean. */
  @Bean
  public CreateNoteCommandMapper createNoteCommandMapper() {
    return new CreateNoteCommandMapper(objectMapper);
  }
}
