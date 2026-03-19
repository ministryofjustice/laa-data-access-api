package uk.gov.justice.laa.dstew.access.utils.generator.caseworker;

import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class CaseworkerUnassignRequestGenerator extends BaseGenerator<CaseworkerUnassignRequest, CaseworkerUnassignRequest.Builder> {

    public CaseworkerUnassignRequestGenerator() {
        super(CaseworkerUnassignRequest::toBuilder, CaseworkerUnassignRequest.Builder::build);
    }

    @Override
    public CaseworkerUnassignRequest createDefault() {
        return CaseworkerUnassignRequest.builder()
                .eventHistory(EventHistory.builder()
                        .eventDescription("Testing unassignment")
                        .build())
                .build();
    }
}
