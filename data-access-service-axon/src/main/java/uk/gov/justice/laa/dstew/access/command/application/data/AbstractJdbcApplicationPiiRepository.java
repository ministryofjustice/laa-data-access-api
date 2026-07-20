package uk.gov.justice.laa.dstew.access.command.application.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationPiiRepository.ExistingFragment;

abstract class AbstractJdbcApplicationPiiRepository implements ApplicationPiiRepository {

  private static final String FIND_LATEST_SQL =
      """
      SELECT fragment_ref, content_hash
      FROM axon.pii_records
      WHERE application_id = ?
        AND section_name = ?
        AND pii_status = 'PRESENT'
      ORDER BY saved_at DESC
      LIMIT 1
      """;

  private static final String FIND_FRAGMENTS_SQL =
      """
      SELECT fragment_ref, encrypted_payload
      FROM axon.pii_records
      WHERE fragment_ref::text IN (:refs)
        AND pii_status = 'PRESENT'
      """;

  private static final String SAVE_FRAGMENT_SQL =
      """
      INSERT INTO axon.pii_records
        (fragment_ref, application_id, section_name, content_hash, encrypted_payload, saved_at, pii_status)
      VALUES (?, ?, ?, ?, ?, ?, 'PRESENT')
      ON CONFLICT (fragment_ref) DO NOTHING
      """;

  private static final String REDACT_SQL =
      """
      UPDATE axon.pii_records SET pii_status = 'REDACTED' WHERE application_id = ?
      """;

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedParamJdbcTemplate;

  protected AbstractJdbcApplicationPiiRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedParamJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
  }

  @Override
  @Transactional
  public void saveFragment(
      UUID applicationId,
      UUID fragmentRef,
      String sectionName,
      byte[] content,
      Instant savedAt) {
    jdbcTemplate.update(
        SAVE_FRAGMENT_SQL,
        fragmentRef,
        applicationId,
        sectionName,
        contentHash(content),
        encode(content),
        Timestamp.from(savedAt));
  }

  @Override
  public Optional<ExistingFragment> findLatestFragment(UUID applicationId, String sectionName) {
    try {
      return Optional.ofNullable(
          jdbcTemplate.queryForObject(
              FIND_LATEST_SQL,
              (rs, rowNum) ->
                  new ExistingFragment(
                      UUID.fromString(rs.getString("fragment_ref")), rs.getString("content_hash")),
              applicationId,
              sectionName));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  public Map<UUID, byte[]> findFragments(Set<UUID> fragmentRefs) {
    if (fragmentRefs.isEmpty()) {
      return Map.of();
    }
    MapSqlParameterSource params =
        new MapSqlParameterSource("refs", fragmentRefs.stream().map(UUID::toString).toList());
    Map<UUID, byte[]> result = new HashMap<>();
    namedParamJdbcTemplate.query(
        FIND_FRAGMENTS_SQL,
        params,
        (ResultSet rs) -> {
          UUID ref = UUID.fromString(rs.getString("fragment_ref"));
          result.put(ref, decode(rs.getBytes("encrypted_payload")));
        });
    return result;
  }

  @Override
  @Transactional
  public void redactAllForApplication(UUID applicationId, String reason, String actor) {
    jdbcTemplate.update(REDACT_SQL, applicationId);
    jdbcTemplate.update(
        """
        INSERT INTO axon.pii_redaction_audit (application_id, reason, actor)
        VALUES (?, ?, ?)
        """,
        applicationId,
        reason,
        actor);
  }

  /** Returns the SHA-256 hex of the content bytes for duplicate detection. */
  private String contentHash(byte[] content) {
    try {
      byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(content);
      return java.util.HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /** Encodes raw fragment bytes for storage (e.g. encryption). Identity for plain repos. */
  protected abstract byte[] encode(byte[] rawBytes);

  /** Decodes stored bytes back to raw fragment bytes (e.g. decryption). Identity for plain repos. */
  protected abstract byte[] decode(byte[] storedBytes);
}
