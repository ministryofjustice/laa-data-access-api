---
description: Review the codebase against a plan and provide feedback on whether the plan has been followed.
---

Review the current code changes against the plan at ${input:planPath}.

Steps:

1. Read the plan file at ${input:planPath}.
2. Run 'git diff' to see the actual code changes.
3. Compare the changes against the plan and provide feedback on whether the plan has been followed.
4. Check for common gaps:
    - Tests: Does the plan include tests? If so, are they present in the code?
    - Edge cases: Did the implementation cover the cases mentioned in the plan? If not, what is missing?
    - Naming conventions: Does the code follow the naming conventions mentioned in the plan? If not, what is missing?
5. Output a concise review of the code against the plan, highlighting any discrepancies or areas for improvement.
