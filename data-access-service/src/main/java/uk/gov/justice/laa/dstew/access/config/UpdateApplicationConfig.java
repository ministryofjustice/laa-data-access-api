package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.controller.application.UpdateApplicationCommandMapper;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.updateapplication.UpdateApplicationUseCase;

/** Spring configuration that wires all beans for the updateApplication use case. */
@Configuration
@RequiredArgsConstructor
public class UpdateApplicationConfig {

  private final SaveDomainEventService saveDomainEventService;
  private final ApplicationGateway applicationGateway;

  /**
   * Creates the {@link UpdateApplicationUseCase} bean.
   *
   * @return a fully configured use case
   */
  @Bean
  public UpdateApplicationUseCase updateApplicationUseCase() {
    return new UpdateApplicationUseCase(applicationGateway, saveDomainEventService);
  }

  /** Creates the {@link UpdateApplicationCommandMapper} bean. */
  @Bean
  public UpdateApplicationCommandMapper updateApplicationCommandMapper() {
    return new UpdateApplicationCommandMapper();
  }
}
