package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryResult;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ApplicationSummaryGenerator extends BaseGenerator<ApplicationSummaryResult, ApplicationSummaryGenerator.Builder> {

    public ApplicationSummaryGenerator() {
        super(r -> new Builder(r), Builder::build);
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
        private String clientFirstName;
        private String clientLastName;
        private LocalDate clientDateOfBirth;

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
            this.clientFirstName = source.getClientFirstName();
            this.clientLastName = source.getClientLastName();
            this.clientDateOfBirth = source.getClientDateOfBirth();
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
        public Builder clientFirstName(String clientFirstName) { this.clientFirstName = clientFirstName; return this; }
        public Builder clientLastName(String clientLastName) { this.clientLastName = clientLastName; return this; }
        public Builder clientDateOfBirth(LocalDate clientDateOfBirth) { this.clientDateOfBirth = clientDateOfBirth; return this; }

        public ApplicationSummaryResult build() {
            UUID _id = id; ApplicationStatus _status = status; String _laaReference = laaReference;
            String _officeCode = officeCode; Instant _submittedAt = submittedAt; Instant _modifiedAt = modifiedAt;
            Boolean _udf = usedDelegatedFunctions; CategoryOfLaw _col = categoryOfLaw;
            MatterType _mt = matterType; Boolean _iag = isAutoGranted; Boolean _isLead = isLead;
            UUID _caseworkerId = caseworkerId;
            String _clientFirstName = clientFirstName; String _clientLastName = clientLastName;
            LocalDate _clientDateOfBirth = clientDateOfBirth;
            return new ApplicationSummaryResult() {
                @Override public UUID getId() { return _id; }
                @Override public ApplicationStatus getStatus() { return _status; }
                @Override public String getLaaReference() { return _laaReference; }
                @Override public String getOfficeCode() { return _officeCode; }
                @Override public Instant getSubmittedAt() { return _submittedAt; }
                @Override public Instant getModifiedAt() { return _modifiedAt; }
                @Override public Boolean getUsedDelegatedFunctions() { return _udf; }
                @Override public CategoryOfLaw getCategoryOfLaw() { return _col; }
                @Override public MatterType getMatterType() { return _mt; }
                @Override public Boolean getIsAutoGranted() { return _iag; }
                @Override public Boolean getIsLead() { return _isLead; }
                @Override public UUID getCaseworkerId() { return _caseworkerId; }
                @Override public String getClientFirstName() { return _clientFirstName; }
                @Override public String getClientLastName() { return _clientLastName; }
                @Override public LocalDate getClientDateOfBirth() { return _clientDateOfBirth; }
            };
        }
    }
}
