package uk.gov.justice.laa.dstew.access.query.application.history;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.query.groupassignment.GroupAssignmentGroupRow;

/** Application history projection's tracked linked-group assignment row. */
@Entity
@Table(name = "application_history_assignment_group")
@NoArgsConstructor
public class ApplicationHistoryAssignmentGroupRow extends GroupAssignmentGroupRow {}
