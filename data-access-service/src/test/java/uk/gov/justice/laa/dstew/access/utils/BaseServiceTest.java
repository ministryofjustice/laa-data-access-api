package uk.gov.justice.laa.dstew.access.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
public class BaseServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
}
