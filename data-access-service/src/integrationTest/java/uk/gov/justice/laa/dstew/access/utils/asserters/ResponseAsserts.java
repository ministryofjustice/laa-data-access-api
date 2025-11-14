package uk.gov.justice.laa.dstew.access.utils.asserters;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

public class ResponseAsserts {

    public static void assertOK(MvcResult response) {
        assertEquals(response.getResponse().getStatus(), HttpStatus.OK.value());
    }

    public static void assertBadRequest(MvcResult response) {
        assertEquals(response.getResponse().getStatus(), HttpStatus.BAD_REQUEST.value());
    }
}
