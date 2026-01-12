package uk.gov.justice.laa.dstew.access.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Builder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.enums.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.enums.MatterType;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Test class for ApplicationMapperService
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationMapperServiceTest extends BaseServiceTest {
  private static String submittedAt;

  @Autowired
  ApplicationMapperService serviceUnderTest;
  @Autowired
  ObjectMapper objectMapper;


  @BeforeEach
  void setUp() {
  }

  @ParameterizedTest
  @MethodSource("provideProceedingsForMapping")
  void mapToApplicationEntity_SuccessfullyMapFromApplicationContentFields(
      Map<String, Object> appContentMap,
      boolean expectedUseDelegatedFunctions,
      boolean autoGranted) {


    // Given
    ApplicationCreateRequest application =
        applicationCreateRequestFactory.createDefault(builder -> builder.applicationContent(appContentMap));

    // When
    ApplicationEntity entity = serviceUnderTest.toApplicationEntity(application, 1);

    // Then
    assertAll(
        () -> assertEquals(expectedUseDelegatedFunctions, entity.isUseDelegatedFunctions()),
        () -> assertEquals(autoGranted, entity.isAutoGranted()),
        () -> assertEquals(submittedAt, entity.getSubmittedAt().toString())


    );
  }

  @ParameterizedTest
  @MethodSource("invalidApplicationContentProvider")
  void toApplicationEntity_InvalidApplicationContent_ThrowsValidationException(
      Map<String, Object> appContentMap, String expectedMessage) {
    // Given
    ApplicationCreateRequest application =
        applicationCreateRequestFactory.createDefault(builder -> builder.applicationContent(appContentMap));
    // When / Then
    ValidationException caughtException = assertThrows(ValidationException.class, () ->
        serviceUnderTest.toApplicationEntity(application, 1));
    assertEquals(ValidationException.class, caughtException.getClass());
    assertEquals(expectedMessage, caughtException.errors().getFirst());
  }

  private Map<String, Object> getAppContentMap(boolean autoGrant, List<ProceedingJsonObject> proceedings) {
    submittedAt = "2026-01-15T10:20:30Z";
    AppContentJsonObject applicationContent = AppContentJsonObject.builder()
        .proceedings(proceedings)
        .autoGrant(autoGrant)
        .id("f1e2d3c4-b5a6-7d8e-9f0a-1b2c3d4e5f6g")
        .submittedAt(submittedAt)
        .build();

    return getContentMap(applicationContent);
  }


  private static ProceedingJsonObject getProceedingJsonObject(boolean useDelegatedFunctions, boolean leadProceeding) {
    return ProceedingJsonObject.builder()
        .id("f6e2c4e1-5d32-4c3e-9f0a-1e2b3c4d5e6f")
        .leadProceeding(leadProceeding)
        .categoryOfLaw(CategoryOfLaw.Family.name())
        .matterType(MatterType.SCA.name())
        .useDelegatedFunctions(useDelegatedFunctions)
        .build();
  }

  private Stream<Arguments> provideProceedingsForMapping() {
    //App Content Map, expected useDelegatedFunctions, isAutoGrant
    return Stream.of(
        Arguments.of(getAppContentMap(
            false, List.of(getProceedingJsonObject(true, true))), true, false),
        Arguments.of(getAppContentMap(
            true, List.of(getProceedingJsonObject(false, true))), false, true),
        Arguments.of(getAppContentMap(
            true, List.of(getProceedingJsonObject(false, true),
                getProceedingJsonObject(true, false))), true, true),
        Arguments.of(getAppContentMap(
            true, List.of(getProceedingJsonObject(false, true),
                getProceedingJsonObject(false, false))), false, true)
    );
  }


  private Map<String, Object> getContentMap(AppContentJsonObject applicationContent) {
    Map<String, Object> appContentMap;
    try {
      appContentMap = objectMapper.readValue(
          objectMapper.writeValueAsString(applicationContent), Map.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return appContentMap;
  }

  public Stream<Arguments> invalidApplicationContentProvider() {
    return Stream.of(
        Arguments.of(
            getAppContentMap(
                true, List.of()),
            "No proceedings found in application content"
        ),
        Arguments.of(
            getAppContentMap(
                true, null),
            "No proceedings found in application content"
        ),
        Arguments.of(
            getAppContentMap(
                false, List.of(getProceedingJsonObject(true, false))),
            "No lead proceeding found in application content"
        )
    );
  }

  /**
   * Application Content JSON Object for testing
   *
   * @param proceedings
   * @param id
   * @param autoGrant
   * @param submittedAt
   */
  @Builder
  record AppContentJsonObject(
      List<ProceedingJsonObject> proceedings,
      String id,
      boolean autoGrant,
      String submittedAt) {
  }

  /**
   * Proceeding JSON Object for testing
   *
   * @param id
   * @param leadProceeding        is this the lead proceeding
   * @param categoryOfLaw         categoryOfLaw as string
   * @param matterType            matterType as string
   * @param useDelegatedFunctions useDelegatedFunctions flag
   */
  @Builder
  record ProceedingJsonObject(
      String id,
      boolean leadProceeding,
      String categoryOfLaw,
      String matterType,
      boolean useDelegatedFunctions) {
  }
}