package uk.gov.justice.laa.dstew.access.query.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;

/** Application and related read models required to build an Application response. */
public record ApplicationDetailResult(
    ApplicationReadModel application,
    LinkedApplicationGroupReadModel linkedGroup,
    List<PriorAuthorityReadModel> priorAuthorities,
    Map<UUID, LinkedApplicationMemberDetails> linkedMemberDetails) {}
