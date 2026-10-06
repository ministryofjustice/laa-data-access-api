package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.datafaker.Faker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FullJsonGeneratorTest {

  @ParameterizedTest
  @ValueSource(longs = {1L, 42L, 123456789L})
  void generatesValidUniqueConsistentReferences(long seed) {
    var generator =
        new FullJsonGenerator() {
          {
            faker = new Faker(new Random(seed));
          }
        };
    Set<String> references = new HashSet<>();
    Set<Character> characters = new HashSet<>();
    for (int index = 0; index < 100; index++) {
      var content = generator.createDefault();
      String reference = content.getLaaReference();
      assertTrue(reference.matches("L-[0-9ABCDEFHJKLMNPRTUVWXY]{3}-[0-9ABCDEFHJKLMNPRTUVWXY]{3}"));
      assertTrue(references.add(reference));
      assertEquals(reference, content.getAdditionalApplicationContent().get("applicationRef"));
      reference.substring(2).chars().forEach(character -> characters.add((char) character));
    }
    assertTrue(characters.contains('U'));
    assertTrue(characters.contains('0'));
  }
}