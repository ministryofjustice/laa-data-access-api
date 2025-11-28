package uk.gov.justice.laa.dstew.access.utils.testData;

import org.junit.jupiter.params.provider.Arguments;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

public class ApplicationTestData {

    public static class Create {

        public static final String TO_CREATE_REFERENCE = "REF7327";
        public static final ApplicationCreateRequest TO_CREATE = ApplicationCreateRequest.builder()
                .status(ApplicationStatus.IN_PROGRESS)
                .applicationReference(TO_CREATE_REFERENCE)
                .applicationContent(new HashMap<>() {{
                    put("test", "content");
                }})
                .build();

        public static final Stream<Arguments> INVALID_REQUESTS =
            Stream.of(
                Arguments.of(ApplicationCreateRequest.builder()
                                .status(ApplicationStatus.IN_PROGRESS)
                                .applicationReference(TO_CREATE_REFERENCE)
                                .applicationContent(null)
                                .build(),
                        new ValidationException(List.of(
                                "ApplicationCreateRequest and its content cannot be null"
                        ))
                ),
                Arguments.of(ApplicationCreateRequest.builder()
                                .applicationReference(TO_CREATE_REFERENCE)
                                .applicationContent(new HashMap<>() {{
                                    put("test", "content");
                                }})
                                .build(),
                        new ValidationException(List.of(
                                "Application status cannot be null"
                        ))
                ),
                Arguments.of(ApplicationCreateRequest.builder()
                            .status(ApplicationStatus.IN_PROGRESS)
                            .applicationContent(new HashMap<>() {{
                                put("test", "content");
                            }})
                            .build(),
                        new ValidationException(List.of(
                                "Application reference cannot be blank"
                        ))
                ),
                Arguments.of(ApplicationCreateRequest.builder()
                                .status(ApplicationStatus.IN_PROGRESS)
                                .applicationReference(TO_CREATE_REFERENCE)
                                .applicationContent(new HashMap<>())
                                .build(),
                        new ValidationException(List.of(
                                "Application content cannot be empty"
                        ))
                ),
                // TODO: validation exception to throw all errors...
                Arguments.of(ApplicationCreateRequest.builder()
                                .build(),
                        new ValidationException(List.of(
                                "Application status cannot be null"
                        ))
                )
            );
    }
}
