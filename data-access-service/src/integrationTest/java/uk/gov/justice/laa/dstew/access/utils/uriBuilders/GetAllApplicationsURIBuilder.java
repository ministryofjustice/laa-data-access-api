package uk.gov.justice.laa.dstew.access.utils.uriBuilders;

import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GetAllApplicationsURIBuilder {

    private Integer pageNumber;
    private Integer pageSize;
    private ApplicationStatus statusFilter;

    public GetAllApplicationsURIBuilder withPageNumber(final int pageNumber) {
        this.pageNumber = pageNumber;
        return this;
    }

    public GetAllApplicationsURIBuilder withPageSize(final int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public GetAllApplicationsURIBuilder withStatusFilter(final ApplicationStatus statusFilter) {
        this.statusFilter = statusFilter;
        return this;
    }

    public URI build() throws URISyntaxException {
        StringBuilder uriString =  new StringBuilder();
        uriString.append(TestConstants.URIs.GET_ALL_APPLICATIONS);

        if (ifAnyPropertySet()) {
            uriString.append("?");
            List<String> queryParams  = new ArrayList<>();

            if (statusFilter != null) {
                queryParams.add(TestConstants.URIs.GET_ALL_APPLICATIONS_STATUS_PARAM + statusFilter);
            }

            if (pageNumber != null) {
                queryParams.add(TestConstants.URIs.GET_ALL_APPLICATIONS_PAGE_PARAM + pageNumber);
            }

            if (pageSize != null) {
                queryParams.add(TestConstants.URIs.GET_ALL_APPLICATIONS_PAGE_SIZE_PARAM + pageSize);
            }

            uriString.append(String.join("&", queryParams));
        }

        return new URI(uriString.toString());
    }

    private boolean ifAnyPropertySet() {
        return !Objects.isNull(statusFilter) ||
                !Objects.isNull(pageNumber) || !Objects.isNull(pageSize);
    }
}
