package uk.gov.justice.laa.dstew.access.query.application.listindex;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.query.groupassignment.GroupAssignmentApplicationRow;

/** List-index projection's tracked application assignment row. */
@Entity
@Table(name = "application_list_index_assignment_application")
@NoArgsConstructor
public class ApplicationListIndexAssignmentApplicationRow extends GroupAssignmentApplicationRow {}
