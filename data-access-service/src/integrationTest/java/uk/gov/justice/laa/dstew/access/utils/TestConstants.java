package uk.gov.justice.laa.dstew.access.utils;

public class TestConstants {

    public class MediaTypes {
        public static final String APPLICATION_JSON = "application/json";
    }

    public class URIs {
        public static final String GET_APPLICATION = "/api/v0/applications/{id}";
        public static final String CREATE_APPLICATION = "/api/v0/applications";
        public static final String GET_ALL_APPLICATIONS = "/api/v0/applications";
    }

    public class Roles {
        public static final String READER = "APPROLE_ApplicationReader";
        public static final String WRITER = "APPROLE_ApplicationWriter";
        public static final String UNKNOWN = "Unknown-DO-NOT-IMPLEMENT";
    }
}
