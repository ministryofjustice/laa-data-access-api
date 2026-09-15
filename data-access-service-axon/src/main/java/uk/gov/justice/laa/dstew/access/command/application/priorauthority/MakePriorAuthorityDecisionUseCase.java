package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityByPriorAuthorityIdQuery;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a prior-authority decision command. */
@Component
public class MakePriorAuthorityDecisionUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final QueryGateway queryGateway;

  public MakePriorAuthorityDecisionUseCase(
      RetryingCommandDispatcher dispatcher, QueryGateway queryGateway) {
    this.dispatcher = dispatcher;
    this.queryGateway = queryGateway;
  }

  /** Dispatches the command to the prior-authority aggregate. */
  @AllowApiCaseworker
  public void execute(MakePriorAuthorityDecisionCommand command) {
    PriorAuthorityResult priorAuthorityResult =
        queryGateway
            .query(
                new FindPriorAuthorityByPriorAuthorityIdQuery(command.submissionId()),
                PriorAuthorityResult.class)
            .join();
    if (priorAuthorityResult == null) {
      throw new ResourceNotFoundException(
          "No prior authority found with ID: " + command.submissionId());
    }
    dispatcher.dispatch(
        new ValidateApplicationGrantedCommand(priorAuthorityResult.applicationId()));
    dispatcher.dispatch(command);
  }
}
