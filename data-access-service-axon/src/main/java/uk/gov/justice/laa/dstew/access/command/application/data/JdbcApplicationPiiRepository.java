package uk.gov.justice.laa.dstew.access.command.application.data;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** JDBC-backed PII repository that stores fragment bytes as-is (plain JSON). */
@Component
@Profile("!production & !test")
public class JdbcApplicationPiiRepository extends AbstractJdbcApplicationPiiRepository {

  public JdbcApplicationPiiRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  @Override
  protected byte[] encode(byte[] rawBytes) {
    return rawBytes;
  }

  @Override
  protected byte[] decode(byte[] storedBytes) {
    return storedBytes;
  }
}
