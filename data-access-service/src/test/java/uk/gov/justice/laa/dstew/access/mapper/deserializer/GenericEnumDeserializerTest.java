package uk.gov.justice.laa.dstew.access.mapper.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.deserializer.CategoryOfLawTypeDeserializer;
import uk.gov.justice.laa.dstew.access.deserializer.GenericEnumDeserializer;
import uk.gov.justice.laa.dstew.access.deserializer.MatterTypeDeserializer;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;

@ExtendWith(MockitoExtension.class)
class GenericEnumDeserializerTest {

  @Mock
  DeserializationContext ctx;

  @Mock
  JsonParser jsonParser;

  enum TestEnum {
    ONE,
    TWO,
    THREE
  }

  @ParameterizedTest
  @MethodSource("provideEnumValues")
  void testGenericDeserialize(String input, TestEnum expected) throws Exception {
    GenericEnumDeserializer<TestEnum> deserializer = new GenericEnumDeserializer<>(TestEnum.class);
    when(jsonParser.getText()).thenReturn(input);
    TestEnum result = deserializer.deserialize(jsonParser, ctx);
    assertEquals(expected, result);

  }

  private static Stream<Arguments> provideEnumValues() {
    return Stream.of(
        Arguments.of("ONE", TestEnum.ONE),
        Arguments.of("TWO", TestEnum.TWO),
        Arguments.of("THREE", TestEnum.THREE),
        Arguments.of("one", TestEnum.ONE),
        Arguments.of(" two ", TestEnum.TWO),
        Arguments.of("", null),
        Arguments.of(null, null),
        Arguments.of("INVALID", null)
    );
  }

  @ParameterizedTest
  @MethodSource("provideMatterTypeEnumValues")
  void testMatterTypeDeserialize(String input, MatterType expected) throws Exception {
    MatterTypeDeserializer matterTypeDeserializer = new MatterTypeDeserializer();
    when(jsonParser.getText()).thenReturn(input);
    MatterType result = matterTypeDeserializer.deserialize(jsonParser, ctx);
    assertEquals(expected, result);
  }

  private static Stream<Arguments> provideMatterTypeEnumValues() {
    return Stream.of(
        Arguments.of("SPECIAL_CHILDREN_ACT", MatterType.SPECIAL_CHILDREN_ACT),
        Arguments.of("special_children_act", MatterType.SPECIAL_CHILDREN_ACT),
        Arguments.of("", null),
        Arguments.of(null, null),
        Arguments.of("INVALID", null)
    );
  }

  @ParameterizedTest
  @MethodSource("provideCategoryOfLawTypeEnumValues")
  void testCategoryOfLawTypeDeserialize(String input, CategoryOfLaw expected) throws Exception {
    CategoryOfLawTypeDeserializer categoryOfLawTypeDeserializer = new CategoryOfLawTypeDeserializer();
    when(jsonParser.getText()).thenReturn(input);
    CategoryOfLaw result = categoryOfLawTypeDeserializer.deserialize(jsonParser, ctx);
    assertEquals(expected, result);
  }


  private static Stream<Arguments> provideCategoryOfLawTypeEnumValues() {
    return Stream.of(
        Arguments.of("Family", CategoryOfLaw.FAMILY),
        Arguments.of("FAMILY", CategoryOfLaw.FAMILY),
        Arguments.of("", null),
        Arguments.of(null, null),
        Arguments.of("INVALID", null)
    );
  }

}