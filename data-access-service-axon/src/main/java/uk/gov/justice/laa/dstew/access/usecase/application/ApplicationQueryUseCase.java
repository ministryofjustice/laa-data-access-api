package uk.gov.justice.laa.dstew.access.usecase.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationNotesResult;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsQuery;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.FindNotesForApplicationQuery;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel;
import uk.gov.justice.laa.dstew.access.query.application.history.FindApplicationHistoryQuery;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Secured use case for Application read operations. */
@Service
public class ApplicationQueryUseCase {

  private final QueryGateway queryGateway;

  public ApplicationQueryUseCase(QueryGateway queryGateway) {
    this.queryGateway = queryGateway;
  }

  /** Returns a page of application summaries. */
  @AllowApiCaseworker
  public FindAllApplicationsResult getApplications(FindAllApplicationsQuery query) {
    return queryGateway.query(query, FindAllApplicationsResult.class).join();
  }

  /** Returns the requested application or throws when absent. */
  @AllowApiCaseworker
  public ApplicationReadModel getApplicationById(UUID id) {
    ApplicationReadModel application =
        queryGateway.query(new FindApplicationByIdQuery(id), ApplicationReadModel.class).join();
    if (application == null) {
      throw new ResourceNotFoundException("No application found with ID: " + id);
    }
    return application;
  }

  /** Returns the stored certificate for an application. */
  @AllowApiCaseworker
  public Map<String, Object> getCertificate(UUID id) {
    ApplicationReadModel application = getApplicationById(id);
    if (application.getCertificate() == null) {
      throw new ResourceNotFoundException("No certificate found for application id: " + id);
    }
    return application.getCertificate();
  }

  /** Returns domain-event history for an application. */
  @AllowApiCaseworker
  public List<ApplicationHistoryReadModel> getApplicationHistory(
      UUID id, List<String> requestedTypes) {
    return queryGateway
        .queryMany(
            new FindApplicationHistoryQuery(id, requestedTypes), ApplicationHistoryReadModel.class)
        .join();
  }

  /** Returns notes for an application or throws when the application is absent. */
  @AllowApiCaseworker
  public ApplicationNotesResult getNotesForApplication(UUID id) {
    ApplicationNotesResult result =
        queryGateway
            .query(new FindNotesForApplicationQuery(id), ApplicationNotesResult.class)
            .join();
    if (result == null) {
      throw new ResourceNotFoundException("No application found with ID: " + id);
    }
    return result;
  }
}
