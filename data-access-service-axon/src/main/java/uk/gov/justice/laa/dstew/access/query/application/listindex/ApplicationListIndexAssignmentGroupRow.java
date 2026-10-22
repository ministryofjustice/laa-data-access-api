package uk.gov.justice.laa.dstew.access.query.application.listindex;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.query.groupassignment.GroupAssignmentGroupRow;

/** List-index projection's tracked linked-group assignment row. */
@Entity
@Table(name = "application_list_index_assignment_group")
@NoArgsConstructor
public class ApplicationListIndexAssignmentGroupRow extends GroupAssignmentGroupRow {}
