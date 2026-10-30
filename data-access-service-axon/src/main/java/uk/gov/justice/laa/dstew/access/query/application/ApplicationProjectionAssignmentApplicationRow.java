package uk.gov.justice.laa.dstew.access.query.application;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.query.groupassignment.GroupAssignmentApplicationRow;

/** Application projection's tracked application assignment row. */
@Entity
@Table(name = "application_projection_assignment_application")
@NoArgsConstructor
public class ApplicationProjectionAssignmentApplicationRow extends GroupAssignmentApplicationRow {}
