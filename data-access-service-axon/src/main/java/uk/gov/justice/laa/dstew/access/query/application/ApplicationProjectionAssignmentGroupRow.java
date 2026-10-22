package uk.gov.justice.laa.dstew.access.query.application;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.query.groupassignment.GroupAssignmentGroupRow;

/** Application projection's tracked linked-group assignment row. */
@Entity
@Table(name = "application_projection_assignment_group")
@NoArgsConstructor
public class ApplicationProjectionAssignmentGroupRow extends GroupAssignmentGroupRow {}
