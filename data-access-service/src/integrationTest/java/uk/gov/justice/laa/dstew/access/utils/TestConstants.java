package uk.gov.justice.laa.dstew.access.utils;

public class TestConstants {

    public static class MediaTypes {
        public static final String APPLICATION_JSON = "application/json";
    }

    public static class URIs {
        public static final String GET_APPLICATION = "/api/v0/applications/{id}";
        public static final String CREATE_APPLICATION = "/api/v0/applications";
        public static final String GET_APPLICATIONS = "/api/v0/applications";

        public static final String GET_ALL_APPLICATIONS_PAGE_PARAM = "page=";
        public static final String GET_ALL_APPLICATIONS_PAGE_SIZE_PARAM = "pagesize=";
        public static final String GET_ALL_APPLICATIONS_STATUS_PARAM = "status=";
        public static final String GET_ALL_APPLICATIONS_FIRSTNAME_PARAM = "firstname=";
        public static final String GET_ALL_APPLICATIONS_LASTNAME_PARAM = "lastname=";
    }

    public static class Roles {
        public static final String READER = "APPROLE_ApplicationReader";
        public static final String WRITER = "APPROLE_ApplicationWriter";
        public static final String UNKNOWN = "Unknown-DO-NOT-IMPLEMENT";
    }
}
