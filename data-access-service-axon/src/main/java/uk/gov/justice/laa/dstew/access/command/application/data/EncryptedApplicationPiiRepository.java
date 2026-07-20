package uk.gov.justice.laa.dstew.access.command.application.data;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** JDBC-backed PII repository that encrypts fragment bytes before persistence. */
@Component
@Primary
@Profile("production")
public class EncryptedApplicationPiiRepository extends AbstractJdbcApplicationPiiRepository {

  private final EncryptionService encryptionService;

  public EncryptedApplicationPiiRepository(
      JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
    super(jdbcTemplate);
    this.encryptionService = encryptionService;
  }

  @Override
  protected byte[] encode(byte[] rawBytes) {
    try {
      return encryptionService.encrypt(new String(rawBytes, StandardCharsets.UTF_8));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to encrypt PII fragment", exception);
    }
  }

  @Override
  protected byte[] decode(byte[] storedBytes) {
    try {
      return encryptionService.decrypt(storedBytes).getBytes(StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to decrypt PII fragment", exception);
    }
  }
}
