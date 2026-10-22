package uk.gov.justice.laa.dstew.access.query.worklist;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.query.groupassignment.GroupAssignmentApplicationRow;

/** Work-list projection's tracked application assignment row. */
@Entity
@Table(name = "work_list_assignment_application")
@NoArgsConstructor
public class WorkListAssignmentApplicationRow extends GroupAssignmentApplicationRow {}
