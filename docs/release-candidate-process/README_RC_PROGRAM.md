# Release Candidate (RC) Program: Documentation Index

**Quick Navigation** | **Status**: Ready for Implementation  
**Last Updated**: 21 May 2026

---

## 📋 Main Documents

### 1. **[IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)** — Master Plan
Start here if you're new to the RC program.

**Contains**:
- Executive summary
- Problem statement and solution
- 4-phase implementation timeline
- Resource requirements and effort estimates
- Risk assessment and mitigation
- Success metrics
- Decision log

**For**: Project managers, tech leads, stakeholders  
**Read time**: 20-30 minutes

---

## 👥 Role-Specific Guides

### 2. **[docs/RC_WORKFLOW.md](./docs/RC_WORKFLOW.md)** — Team Operations
How to create, manage, and promote RCs.

**Contains**:
- Decision criteria: "Should we create an RC?"
- Step-by-step RC creation process
- Client notification templates
- Issue triage and response procedures
- RC promotion to Staging
- Troubleshooting guide
- FAQ and checklists

**For**: Tech leads, developers, DevOps engineers  
**Read time**: 15-20 minutes

**Key task**: Creating your first RC? Follow the checklist in Step 1.

---

### 3. **[docs/CLIENT_INTEGRATION_GUIDE.md](./docs/CLIENT_INTEGRATION_GUIDE.md)** — For Clients
How external clients test against RC environments.

**Contains**:
- What is an RC?
- Quick start (5-step process)
- Testing recommendations
- Release notes interpretation
- How to report issues
- Troubleshooting
- FAQ

**For**: External clients, client success teams  
**Read time**: 10-15 minutes

**Key task**: Send this to clients when RC is available.

---

### 4. **[docs/RC_OPERATIONS.md](./docs/RC_OPERATIONS.md)** — Operational Procedures
Day-to-day operations, monitoring, troubleshooting.

**Contains**:
- Deployment procedures (automatic and manual)
- Post-deployment verification
- Continuous monitoring checklist
- Troubleshooting common issues
- Operational tasks (scaling, restarts, cleanup)
- Client issue response procedures
- Emergency procedures
- On-call handover template

**For**: DevOps engineers, on-call engineers  
**Read time**: 20-25 minutes

**Key task**: Keep this open during RC deployment; follow verification steps.

---

### 5. **[docs/HELM_RC_VALUES.md](./docs/HELM_RC_VALUES.md)** — Technical Reference
Detailed explanation of Helm values and configuration.

**Contains**:
- Every values setting explained
- Why RC settings differ from other environments
- Comparison with preview/UAT/Staging
- Advanced customization options
- Resource adjustment guide
- Verification procedures
- Troubleshooting configuration issues

**For**: Platform engineers, Kubernetes operators  
**Read time**: 15-20 minutes

**Key task**: Understand resource allocation before modifying rc.yaml.

---

## 🔧 Configuration Files

### 6. **[.helm/data-access-api/values/rc.yaml](./.helm/data-access-api/values/rc.yaml)** — RC Values File
Helm values for RC environment deployment.

**Created**: ✅ Ready to use  
**Key settings**:
- `replicaCount: 2` (higher than previews)
- `resources.memory: 2G` (double UAT)
- `autoscaling.maxReplicas: 8` (scales generously)
- `spring.profile: "preview"` (set by `deploy_branch` action — same as all ephemeral branch environments)
- `sentry.environment: "rc"` (for error tracking)

**When to edit**: Only if adjusting resource allocation; see RC_OPERATIONS.md for guidance.

---

## 📊 Implementation Status

| Phase | Task | Status | Owner | Duration |
|-------|------|--------|-------|----------|
| **Phase 1** | Create RC values file | ✅ Done | - | - |
| **Phase 1** | Add deploy-rc job to workflow | ✅ Done | - | - |
| **Phase 2** | Documentation complete | ✅ Done | - | - |
| **Phase 3** | Deploy first RC to staging | ⏳ TODO | DevOps | 1h |
| **Phase 3** | Smoke test RC | ⏳ TODO | QA | 30m |
| **Phase 3** | Pilot with first client | ⏳ TODO | Product | 1-2w |
| **Phase 4** | Expand to all clients | ⏳ TODO | Product | Ongoing |

---

## 🚀 Quick Start Paths

### For: Tech Lead / Project Manager
1. Read: [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) (20 min)
2. Review: Timeline and effort estimates (Phase 1)
3. Schedule: Kick-off meeting with team
4. Assign: Owners for each implementation phase

### For: DevOps / Platform Engineer
1. Read: [docs/RC_OPERATIONS.md](./docs/RC_OPERATIONS.md) (20 min)
2. Review: [docs/HELM_RC_VALUES.md](./docs/HELM_RC_VALUES.md) (15 min)
3. Verify: `.helm/data-access-api/values/rc.yaml` is in place
4. Next: Implement Phase 1, Step 1.2 (add deploy-rc job to workflow)

### For: Product Manager
1. Read: [docs/CLIENT_INTEGRATION_GUIDE.md](./docs/CLIENT_INTEGRATION_GUIDE.md) (15 min)
2. Identify: Pilot clients who should test first RC
3. Plan: Client communication strategy
4. Prepare: Draft release notes template

### For: External Client
1. Read: [docs/CLIENT_INTEGRATION_GUIDE.md](./docs/CLIENT_INTEGRATION_GUIDE.md) (15 min)
2. Wait: For email notification of RC availability
3. Follow: "Quick Start" section (5 steps)
4. Test: Run integration tests against RC endpoint
5. Report: Feedback to support team

---

## 📞 Support & Escalation

### Implementation Questions
- **Contact**: Tech Lead
- **Response time**: Same day
- **Topics**: Design decisions, configuration, process questions

### Operational Issues
- **Contact**: On-call DevOps engineer
- **Response time**: 2 hours (critical), 4 hours (high)
- **Topics**: RC down, pod issues, deployment failures

### Client Issues
- **Contact**: Product Manager → Support Team
- **Response time**: 2 hours (acknowledge), 24 hours (resolve)
- **Topics**: API errors, integration questions, feature requests

---

## 🎯 Success Metrics

Track these metrics monthly to assess RC program success:

| Metric | Target | Owner |
|--------|--------|-------|
| **Client participation** | >50% of eligible clients | Product |
| **Issues caught early** | >50% of breaking changes found in RC | QA |
| **RC stability** | >99% uptime | DevOps |
| **Issue response** | 2h acknowledge, 24h resolve | Support |
| **Client satisfaction** | >7/10 NPS | Product |

---

## 📚 Additional Resources

### Related Documentation in This Repo
- `IMPLEMENTATION_PLAN.md` → Decision log (Appendix B)
- `README.md` → Deployment and infrastructure overview
- `docs/monitoring.md` → Prometheus/Grafana setup for RC
- `.github/workflows/build-test-deploy.yml` → Where deploy-rc job will be added
- `.github/actions/deploy_branch/action.yml` → Action used by deploy-rc

### External Resources
- [Helm Documentation](https://helm.sh/docs/) — Values files, deployment
- [Kubernetes Documentation](https://kubernetes.io/docs/) — Pods, ingress, HPA
- [Cloud Platform Docs](https://user-guide.cloud.service.justice.gov.uk/) — Cluster access, namespaces

---

## 🔄 Document Maintenance

### Updates
- **Frequency**: After each phase completion, or when significant changes made
- **Owner**: Tech Lead
- **Process**: Update relevant doc, update status table above, commit to git

### Feedback
- Found an error or unclear section?
- Feedback: [Create issue or PR with improvements]

### Version History
| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 21 May 2026 | David Stuart | Initial comprehensive documentation |

---

## 🎓 Learning Path (Recommended Reading Order)

**Day 1**: Orientation
- [ ] This file (5 min)
- [ ] [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) — Executive summary section (5 min)

**Day 2-3**: Deep dive (based on your role)
- [ ] Relevant guide from "Role-Specific Guides" section above

**Day 4-5**: Implementation
- [ ] Start Phase 1 tasks (see IMPLEMENTATION_PLAN.md)
- [ ] Keep relevant operational guide open (Operations.md or Workflow.md)

**Ongoing**: Reference
- [ ] Bookmark these docs
- [ ] Check relevant guide before performing task
- [ ] Ask questions in team Slack if unclear

---

## ✅ Next Steps

1. **Read the relevant guide** for your role (see Quick Start Paths above)
2. **Schedule team meeting** (if you're the project lead)
3. **Assign Phase 1 tasks** (see IMPLEMENTATION_PLAN.md, Phase 1)
4. **Start implementation** (follow the roadmap in IMPLEMENTATION_PLAN.md)

**Questions?** Check the FAQ in the relevant guide, then reach out to your team lead.

Good luck! 🚀

