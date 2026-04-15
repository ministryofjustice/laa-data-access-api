package uk.gov.justice.laa.dstew.access.adapter.outbound.event;

import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.domain.model.Application;
import uk.gov.justice.laa.dstew.access.domain.port.inbound.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.domain.port.outbound.DomainEventPort;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;

/**
 * Adapter that bridges the domain {@link DomainEventPort} to the existing {@link
 * DomainEventService}. Translates domain types into the entity and API types that {@code
 * DomainEventService} currently expects.
 *
 * <p>TODO: In a future ticket, refactor {@code DomainEventService} to accept domain types directly
 * so this adapter can be simplified.
 */
@Component
@RequiredArgsConstructor
public class DomainEventAdapter implements DomainEventPort {

  private final DomainEventService domainEventService;

  @Override
  public void publishApplicationCreated(Application application, CreateApplicationCommand command) {

    ApplicationEntity entity = toEntityForEvent(application);
    ApplicationCreateRequest apiRequest = toApiRequest(command);

    domainEventService.saveCreateApplicationDomainEvent(entity, apiRequest, null);
  }

  /**
   * Builds a minimal {@link ApplicationEntity} carrying the fields that {@link
   * DomainEventService#saveCreateApplicationDomainEvent} reads.
   */
  private ApplicationEntity toEntityForEvent(Application application) {
    return ApplicationEntity.builder()
        .id(application.getId())
        .status(application.getStatus())
        .laaReference(application.getLaaReference())
        .createdAt(application.getCreatedAt())
        .build();
  }

  /**
   * Reconstructs an {@link ApplicationCreateRequest} from the command so that the existing event
   * serialisation logic is preserved.
   */
  private ApplicationCreateRequest toApiRequest(CreateApplicationCommand command) {
    var individuals =
        command.individuals() == null
            ? java.util.List.<IndividualCreateRequest>of()
            : command.individuals().stream()
                .map(
                    ind -> {
                      var req = new IndividualCreateRequest();
                      req.setFirstName(ind.getFirstName());
                      req.setLastName(ind.getLastName());
                      req.setDateOfBirth(ind.getDateOfBirth());
                      req.setDetails(ind.getIndividualContent());
                      req.setType(ind.getType());
                      return req;
                    })
                .collect(Collectors.toList());

    var req = new ApplicationCreateRequest();
    req.setStatus(command.status());
    req.setLaaReference(command.laaReference());
    req.setApplicationContent(command.applicationContent());
    req.setIndividuals(individuals);
    return req;
  }
}
