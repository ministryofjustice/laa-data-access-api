package uk.gov.justice.laa.dstew.access.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Builder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.enums.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.enums.MatterType;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;

/**
 * Test class for ApplicationMapperService
 */
class ApplicationMapperServiceTest extends BaseServiceTest {
  static ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  ApplicationMapperService serviceUnderTest;

  @BeforeAll
  static void init() {
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @BeforeEach
  void setUp() {
  }

  @ParameterizedTest
  @MethodSource("provideProceedingsForMapping")
  void mapToApplicationEntity_usedDelegatedFunctionsMapped(
      Map<String, Object> appContentMap,
      boolean expectedUseDelegatedFunctions) {


    // Given
    ApplicationCreateRequest application =
        applicationCreateRequestFactory.createDefault(builder -> builder.applicationContent(appContentMap));

    // When
    ApplicationEntity entity = serviceUnderTest.toApplicationEntity(application, 1);

    // Then
    assertAll(
        () -> assertEquals(expectedUseDelegatedFunctions, entity.isUseDelegatedFunctions())
    );
  }

  private static Map<String, Object> getAppContentMap(List<ProceedingJsonObject> proceedings) {
    AppContentJsonObject applicationContent = AppContentJsonObject.builder()
        .proceedings(proceedings)
        .autoGrant(true)
        .laaReference("L-XCX-0WB")
        .build();

    return getContentMap(applicationContent);
  }


  private static ProceedingJsonObject getProceedingJsonObject(boolean useDelegatedFunctions, boolean leadProceeding) {
    return  ProceedingJsonObject.builder()
        .id("f6e2c4e1-5d32-4c3e-9f0a-1e2b3c4d5e6f")
        .leadProceeding(leadProceeding)
        .categoryOfLaw(CategoryOfLaw.Family.name())
        .matterType(MatterType.SCA.name())
        .useDelegatedFunctions(useDelegatedFunctions)
        .build();
  }

  private static Stream<Arguments> provideProceedingsForMapping() {
    return Stream.of(
        Arguments.of(getAppContentMap(List.of(getProceedingJsonObject(true, true))), true),
        Arguments.of(getAppContentMap(List.of(getProceedingJsonObject(false, true))), false),
        Arguments.of(getAppContentMap(List.of(getProceedingJsonObject(false, true),
            getProceedingJsonObject(true, false))), true),
        Arguments.of(getAppContentMap(List.of(getProceedingJsonObject(false, true),
            getProceedingJsonObject(false, false))), false)
    );
  }


  private static Map<String, Object> getContentMap(AppContentJsonObject applicationContent) {
    Map<String, Object> appContentMap;
    try {
      appContentMap = objectMapper.readValue(
          objectMapper.writeValueAsString(applicationContent), Map.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return appContentMap;
  }

  /**
   * Application Content JSON Object for testing
   * @param proceedings
   * @param laaReference
   * @param autoGrant
   */
  @Builder
  record AppContentJsonObject(
      List<ProceedingJsonObject> proceedings,
      String laaReference,
      boolean autoGrant) {
  }

  /**
   * Proceeding JSON Object for testing
   * @param id
   * @param leadProceeding is this the lead proceeding
   * @param categoryOfLaw categoryOfLaw as string
   * @param matterType matterType as string
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