package uk.gov.justice.laa.dstew.access.command.application;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

/** Coordinates Application creation after its Apply identifier claim has committed. */
@Saga
@ProcessingGroup("create-application-saga")
public class CreateApplicationSaga {

  private transient CommandGateway commandGateway;

  /** Starts finalisation in a separate command unit of work. */
  @StartSaga
  @SagaEventHandler(associationProperty = "applyApplicationId")
  public void on(ApplyApplicationIdClaimedEvent event) {
    SagaLifecycle.associateWith("applicationId", event.applicationId().toString());
    commandGateway.send(
        new FinaliseApplicationCreationCommand(
            event.applicationId(), event.applicationCreatedEvent(), event.leadApplicationId()),
        (commandMessage, resultMessage) -> {
          if (resultMessage.isExceptional()) {
            releaseClaim(event);
          } else if (resultMessage.getPayload() == ApplicationFinalisationResult.ALREADY_CREATED) {
            SagaLifecycle.end();
          }
        });
  }

  /** Ends coordination when the Application aggregate confirms creation. */
  @EndSaga
  @SagaEventHandler(associationProperty = "applicationId")
  public void on(ApplicationCreatedEvent event) {
    // Association and lifecycle annotations perform the state transition.
  }

  private void releaseClaim(ApplyApplicationIdClaimedEvent event) {
    commandGateway.send(
        new ReleaseApplyApplicationIdCommand(event.applyApplicationId(), event.applicationId()),
        (commandMessage, resultMessage) -> SagaLifecycle.end());
  }

  @Autowired
  void setCommandGateway(CommandGateway commandGateway) {
    this.commandGateway = commandGateway;
  }

  public CreateApplicationSaga() {
    // Required by Axon when restoring a persisted saga.
  }
}
