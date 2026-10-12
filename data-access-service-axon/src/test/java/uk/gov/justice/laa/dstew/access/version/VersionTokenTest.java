package uk.gov.justice.laa.dstew.access.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VersionTokenTest {

  private static final UUID RESOURCE_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
  private static final String EXPECTED_TOKEN =
      "djE6bGlua2VkLWdyb3VwOjdjOWU2Njc5LTc0MjUtNDBkZS05NDRiLWUwN2ZjMWY5MGFlNzoy";

  @Test
  void givenLinkedGroupToken_whenEncoded_thenMatchesKnownValue() {
    assertThat(VersionToken.linkedGroup(RESOURCE_ID, 2).encode()).isEqualTo(EXPECTED_TOKEN);
  }

  @Test
  void givenEncodedToken_whenDecoded_thenRoundTrips() {
    var token = VersionToken.linkedGroup(RESOURCE_ID, 2);

    assertThat(VersionToken.decode(token.encode(), VersionedResourceType.LINKED_GROUP))
        .isEqualTo(token);
  }

  @Test
  void givenEncodedToken_whenInspected_thenIsUrlSafeWithoutPadding() {
    var encoded = VersionToken.linkedGroup(RESOURCE_ID, 2).encode();

    assertThat(encoded).matches("^[A-Za-z0-9_-]+$");
  }

  @ParameterizedTest
  @MethodSource("invalidTokens")
  void givenInvalidToken_whenDecoded_thenThrowsIllegalArgument(String token) {
    assertThatThrownBy(() -> VersionToken.decode(token, VersionedResourceType.LINKED_GROUP))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid version token");
  }

  @Test
  void givenNegativeVersion_whenConstructed_thenThrowsIllegalArgument() {
    assertThatThrownBy(() -> VersionToken.linkedGroup(RESOURCE_ID, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("version must not be negative");
  }

  private static Stream<Arguments> invalidTokens() {
    return Stream.of(
        Arguments.of((Object) null),
        Arguments.of(""),
        Arguments.of("   "),
        Arguments.of("!!!"),
        Arguments.of("a+b/"),
        Arguments.of(encoded("v2:linked-group:" + RESOURCE_ID + ":1")),
        Arguments.of(encoded("v1:application:" + RESOURCE_ID + ":1")),
        Arguments.of(encoded("v1:linked-group:" + RESOURCE_ID)),
        Arguments.of(encoded("v1:linked-group:" + RESOURCE_ID + ":1:extra")),
        Arguments.of(encoded("v1:linked-group:not-a-uuid:1")),
        Arguments.of(encoded("v1:linked-group:1-1-1-1-1:1")),
        Arguments.of(encoded("v1:linked-group:" + RESOURCE_ID + ":abc")),
        Arguments.of(encoded("v1:linked-group:" + RESOURCE_ID + ":-1")));
  }

  private static String encoded(String raw) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
