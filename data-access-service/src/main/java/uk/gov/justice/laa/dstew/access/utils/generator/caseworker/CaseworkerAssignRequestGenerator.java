package uk.gov.justice.laa.dstew.access.utils.generator.caseworker;

import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

import java.util.List;
import java.util.UUID;

public class CaseworkerAssignRequestGenerator extends BaseGenerator<CaseworkerAssignRequest, CaseworkerAssignRequest.Builder> {

    public CaseworkerAssignRequestGenerator() {
        super(CaseworkerAssignRequest::toBuilder, CaseworkerAssignRequest.Builder::build);
    }

    @Override
    public CaseworkerAssignRequest createDefault() {
        return CaseworkerAssignRequest.builder()
                .caseworkerId(UUID.randomUUID())
                .applicationIds(List.of(UUID.randomUUID()))
                .build();
    }
}
