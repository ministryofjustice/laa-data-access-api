package uk.gov.justice.laa.dstew.access.query.worklist;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.query.groupassignment.GroupAssignmentGroupRow;

/** Work-list projection's tracked linked-group assignment row. */
@Entity
@Table(name = "work_list_assignment_group")
@NoArgsConstructor
public class WorkListAssignmentGroupRow extends GroupAssignmentGroupRow {}
