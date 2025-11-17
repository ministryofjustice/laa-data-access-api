package uk.gov.justice.laa.dstew.access.utils.asserters;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

public class ResponseAsserts {

    public static void assertSecurityHeaders(MvcResult response) {
        assertEquals("0", response.getResponse().getHeader("X-XSS-Protection"));
        assertEquals("DENY", response.getResponse().getHeader("X-Frame-Options"));
    }

    public static void assertContentHeaders(MvcResult response) {
        assertEquals("application/json", response.getResponse().getContentType());
    }

    public static void assertNoCacheHeaders(MvcResult response) {
        assertEquals("no-cache, no-store, max-age=0, must-revalidate", response.getResponse().getHeader("Cache-Control"));
        assertEquals("no-cache", response.getResponse().getHeader("Pragma"));
        assertEquals("0", response.getResponse().getHeader("Expires"));
    }

    public static void assertOK(MvcResult response) {
        assertEquals(HttpStatus.OK.value(), response.getResponse().getStatus());
    }

    public static void assertNotFound(MvcResult response) {
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getResponse().getStatus());
    }

    public static void assertBadRequest(MvcResult response) {
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getResponse().getStatus());
    }

    public static void assertForbidden(MvcResult response) {
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getResponse().getStatus());
    }

    public static void assertUnauthorised(MvcResult response) {
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getResponse().getStatus());
    }

    public static void assertCreated(MvcResult response) {
        assertEquals(HttpStatus.CREATED.value(), response.getResponse().getStatus());
        assertNotNull(response.getResponse().getHeader("Location"));
    }

    public static void assertNoContent(MvcResult response) {
        assertEquals(HttpStatus.NO_CONTENT.value(), response.getResponse().getStatus());
    }
}