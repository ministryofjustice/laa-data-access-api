package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.controller.application.GetAllNotesForApplicationResponseMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallnotesforapplication.GetAllNotesForApplicationApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallnotesforapplication.GetAllNotesForApplicationGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallnotesforapplication.GetAllNotesForApplicationNoteJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application.ApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication.GetAllNotesForApplicationUseCase;

/** Spring configuration for the get-all-notes-for-application use case. */
@Configuration
@RequiredArgsConstructor
public class GetAllNotesForApplicationConfig {

  private final ApplicationJpaGateway applicationJpaGateway;
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
   * Creates the application gateway bean.
   *
   * @return application gateway
   */
  @Bean
  public GetAllNotesForApplicationApplicationJpaGateway
      getAllNotesForApplicationApplicationGateway() {
    return new GetAllNotesForApplicationApplicationJpaGateway(applicationJpaGateway);
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
   * @param getAllNotesForApplicationApplicationJpaGateway application gateway
   * @param getAllNotesForApplicationNoteJpaGateway note gateway
   * @return use case
   */
  @Bean
  public GetAllNotesForApplicationUseCase getAllNotesForApplicationUseCase(
      GetAllNotesForApplicationApplicationJpaGateway getAllNotesForApplicationApplicationJpaGateway,
      GetAllNotesForApplicationNoteJpaGateway getAllNotesForApplicationNoteJpaGateway) {
    return new GetAllNotesForApplicationUseCase(
        getAllNotesForApplicationApplicationJpaGateway, getAllNotesForApplicationNoteJpaGateway);
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
