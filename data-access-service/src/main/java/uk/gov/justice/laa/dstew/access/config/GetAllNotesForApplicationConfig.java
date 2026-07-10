package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.controller.application.GetAllNotesForApplicationResponseMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallnotesforapplication.GetAllNotesForApplicationGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallnotesforapplication.GetAllNotesForApplicationNoteJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication.GetAllNotesForApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;

/** Spring configuration for the get-all-notes-for-application use case. */
@Configuration
@RequiredArgsConstructor
public class GetAllNotesForApplicationConfig {

  private final NoteRepository noteRepository;

  /**
   * Creates the gateway mapper bean.
   *
   * @return gateway mapper
   */
  @Bean
  public GetAllNotesForApplicationGatewayMapper getAllNotesForApplicationGatewayMapper() {
    return new GetAllNotesForApplicationGatewayMapper();
  }

  /**
   * Creates the note gateway bean.
   *
   * @param getAllNotesForApplicationGatewayMapper gateway mapper
   * @return note gateway
   */
  @Bean
  public GetAllNotesForApplicationNoteJpaGateway getAllNotesForApplicationNoteGateway(
      GetAllNotesForApplicationGatewayMapper getAllNotesForApplicationGatewayMapper) {
    return new GetAllNotesForApplicationNoteJpaGateway(
        noteRepository, getAllNotesForApplicationGatewayMapper);
  }

  /**
   * Creates the use case bean.
   *
   * @param applicationGateway application gateway
   * @param getAllNotesForApplicationNoteJpaGateway note gateway
   * @return use case
   */
  @Bean
  public GetAllNotesForApplicationUseCase getAllNotesForApplicationUseCase(
      ApplicationGateway applicationGateway,
      GetAllNotesForApplicationNoteJpaGateway getAllNotesForApplicationNoteJpaGateway) {
    return new GetAllNotesForApplicationUseCase(
        applicationGateway, getAllNotesForApplicationNoteJpaGateway);
  }

  /**
   * Creates the response mapper bean.
   *
   * @return response mapper
   */
  @Bean
  public GetAllNotesForApplicationResponseMapper getAllNotesForApplicationResponseMapper() {
    return new GetAllNotesForApplicationResponseMapper();
  }
}
