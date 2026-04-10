package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.time.Instant;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryResult;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ApplicationSummaryGenerator extends BaseGenerator<ApplicationSummaryResult, ApplicationSummaryGenerator.Builder> {

    public ApplicationSummaryGenerator() {
        super(Builder::new, Builder::build);
    }

    @Override
    public ApplicationSummaryResult createDefault() {
        return new Builder()
                .id(UUID.randomUUID())
                .laaReference("REF7327")
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .modifiedAt(Instant.now())
                .isLead(false)
                .build();
    }

    @Override
    public ApplicationSummaryResult createRandom() {
        return new Builder()
                .id(UUID.randomUUID())
                .laaReference(faker.bothify("REF####"))
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .modifiedAt(Instant.now())
                .isLead(false)
                .build();
    }

    public static class Builder {
        private UUID id;
        private ApplicationStatus status;
        private String laaReference;
        private String officeCode;
        private Instant submittedAt;
        private Instant modifiedAt;
        private Boolean usedDelegatedFunctions;
        private CategoryOfLaw categoryOfLaw;
        private MatterType matterType;
        private Boolean isAutoGranted;
        private Boolean isLead;
        private UUID caseworkerId;

        public Builder() {}

        public Builder(ApplicationSummaryResult source) {
            this.id = source.getId();
            this.status = source.getStatus();
            this.laaReference = source.getLaaReference();
            this.officeCode = source.getOfficeCode();
            this.submittedAt = source.getSubmittedAt();
            this.modifiedAt = source.getModifiedAt();
            this.usedDelegatedFunctions = source.getUsedDelegatedFunctions();
            this.categoryOfLaw = source.getCategoryOfLaw();
            this.matterType = source.getMatterType();
            this.isAutoGranted = source.getIsAutoGranted();
            this.isLead = source.getIsLead();
            this.caseworkerId = source.getCaseworkerId();
        }

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder status(ApplicationStatus status) { this.status = status; return this; }
        public Builder laaReference(String laaReference) { this.laaReference = laaReference; return this; }
        public Builder officeCode(String officeCode) { this.officeCode = officeCode; return this; }
        public Builder submittedAt(Instant submittedAt) { this.submittedAt = submittedAt; return this; }
        public Builder modifiedAt(Instant modifiedAt) { this.modifiedAt = modifiedAt; return this; }
        public Builder usedDelegatedFunctions(Boolean v) { this.usedDelegatedFunctions = v; return this; }
        public Builder categoryOfLaw(CategoryOfLaw v) { this.categoryOfLaw = v; return this; }
        public Builder matterType(MatterType v) { this.matterType = v; return this; }
        public Builder isAutoGranted(Boolean v) { this.isAutoGranted = v; return this; }
        public Builder isLead(Boolean isLead) { this.isLead = isLead; return this; }
        public Builder caseworkerId(UUID caseworkerId) { this.caseworkerId = caseworkerId; return this; }

        public ApplicationSummaryResult build() {
            return ApplicationSummaryResult.builder()
                    .id(id)
                    .status(status)
                    .laaReference(laaReference)
                    .officeCode(officeCode)
                    .submittedAt(submittedAt)
                    .modifiedAt(modifiedAt)
                    .usedDelegatedFunctions(usedDelegatedFunctions)
                    .categoryOfLaw(categoryOfLaw)
                    .matterType(matterType)
                    .isAutoGranted(isAutoGranted)
                    .isLead(isLead)
                    .caseworkerId(caseworkerId)
                    .build();
        }
    }
}
