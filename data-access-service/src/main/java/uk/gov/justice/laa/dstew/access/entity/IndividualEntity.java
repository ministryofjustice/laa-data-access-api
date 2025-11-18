package uk.gov.justice.laa.dstew.access.entity;

<<<<<<< HEAD
import com.fasterxml.jackson.annotation.JsonFormat;
=======
>>>>>>> 6809ca5 (create individual data models)
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
<<<<<<< HEAD
import java.time.Instant;
import java.time.LocalDate;
=======
>>>>>>> 6809ca5 (create individual data models)
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
<<<<<<< HEAD
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
=======
import org.hibernate.annotations.Type;
>>>>>>> 6809ca5 (create individual data models)
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents an individual.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "individual")
@EntityListeners(AuditingEntityListener.class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
<<<<<<< HEAD
public class IndividualEntity  implements AuditableEntity {
  @Id
  @Column(columnDefinition = "UUID")
  private UUID id;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "date_of_birth", nullable = false)
  private LocalDate dateOfBirth;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> individualContent;

  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "modified_at")
  @UpdateTimestamp
  private Instant modifiedAt;

  @Override
  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public String getCreatedBy() {
    return null;
  }

  @Override
  public Instant getUpdatedAt() {
    return modifiedAt;
  }

  @Override
  public String getUpdatedBy() {
    return null;
  }
=======
public class IndividualEntity {
  @Column
  @Id
  private UUID id;

  @Column
  private String firstName;
  @Column
  private String lastName;

  @Type(JsonType.class)
  @Column(name = "individual_content", columnDefinition = "jsonb")
  private Map<String, Object> details;
>>>>>>> 6809ca5 (create individual data models)
}
