package uk.gov.justice.laa.dstew.access.command.caseworker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Caseworker known to the Axon service and eligible for assignment. */
@Entity
@Table(name = "caseworkers")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Caseworker {

  @Id private UUID id;

  @Column(nullable = false)
  private String username;
}
