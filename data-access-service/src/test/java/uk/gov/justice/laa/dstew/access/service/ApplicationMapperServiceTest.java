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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.enums.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.enums.MatterType;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;

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
  void mapToApplicationEntity_usedDelegatedfunctionsMapped(
      Map<String, Object> appContentMap,
      boolean expectedUseDelegatedFunctions) {


    // Given
    ApplicationCreateRequest application =
        applicationCreateRequestFactory.createDefault(builder -> builder.applicationContent(appContentMap));

    // When
    ApplicationEntity entity = serviceUnderTest.toApplicationEntity(application, 1);

    // Then
    assertAll(
        () -> assertEquals(entity.isUseDelegatedFunctions(), expectedUseDelegatedFunctions)
    );
  }

  private static Map<String, Object> getAppContentMap(boolean useDelegatedFunctions) {
    List<Proceeding> proceedingList = List.of(
        getProceeding(useDelegatedFunctions, true),
        getProceeding(useDelegatedFunctions, false)
    );
    ApplicationContent applicationContent = ApplicationContent.builder()
        .proceedings(proceedingList)
        .autoGrant(true)
        .laaReference("L-XCX-0WB")
        .build();

    return getContentMap(applicationContent);
  }

  private static Stream<Arguments> provideProceedingsForMapping() {
    return Stream.of(
        Arguments.of(getAppContentMap(true), true),
        Arguments.of(getAppContentMap(false), false)
    );
  }


  private static Map<String, Object> getContentMap(ApplicationContent applicationContent) {
    Map<String, Object> appContentMap;
    try {
      appContentMap = objectMapper.readValue(
          objectMapper.writeValueAsString(applicationContent), Map.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return appContentMap;
  }


  private static Proceeding getProceeding(boolean useDelegatedFunctions, boolean leadProceeding) {
    return Proceeding.builder()
        .leadProceeding(leadProceeding)
        .id("f6e2c4e1-5d32-4c3e-9f0a-1e2b3c4d5e6f")
        .categoryOfLaw(CategoryOfLaw.Family)
        .matterType(MatterType.SCA)
        .useDelegatedFunctions(useDelegatedFunctions)
        .build();
  }
}