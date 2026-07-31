package uk.gov.justice.laa.dstew.access.config;

import java.sql.Types;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.BinaryJdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

/**
 * Overrides Hibernate's default OID mapping for {@code @Lob} columns on PostgreSQL, forcing BYTEA
 * instead. This avoids PostgreSQL Large Object Storage for Axon's event, snapshot, and token
 * tables, which prevents large-object table bloat on frequent token updates.
 *
 * @see <a href="https://docs.axoniq.io/axon-framework-reference/4.11/tuning/rdbms-tuning/">Axon
 *     RDBMS tuning</a>
 */
public class ByteaEnforcedPostgresSqlDialect extends PostgreSQLDialect {

  public ByteaEnforcedPostgresSqlDialect() {
    super(DatabaseVersion.make(14));
  }

  @Override
  protected String columnType(int sqlTypeCode) {
    return sqlTypeCode == SqlTypes.BLOB ? "bytea" : super.columnType(sqlTypeCode);
  }

  @Override
  protected String castType(int sqlTypeCode) {
    return sqlTypeCode == SqlTypes.BLOB ? "bytea" : super.castType(sqlTypeCode);
  }

  @Override
  public void contributeTypes(
      TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
    super.contributeTypes(typeContributions, serviceRegistry);
    JdbcTypeRegistry jdbcTypeRegistry =
        typeContributions.getTypeConfiguration().getJdbcTypeRegistry();
    jdbcTypeRegistry.addDescriptor(Types.BLOB, BinaryJdbcType.INSTANCE);
  }
}
