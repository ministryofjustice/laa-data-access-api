package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.util.List;
import java.util.Map;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullMeans;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullMeansGenerator extends BaseGenerator<FullMeans, FullMeans.FullMeansBuilder> {

    private final FullCfeSubmissionGenerator cfeSubmissionGenerator = new FullCfeSubmissionGenerator();

    public FullMeansGenerator() {
        super(FullMeans::toBuilder, FullMeans.FullMeansBuilder::build);
    }

    @Override
    public FullMeans createDefault() {
        return FullMeans.builder()
                .openBanking(Map.of("bankProviders", List.of()))
                .otherAssetsDeclaration(null)
                .savingsAmount(null)
                .dependants(List.of())
                .vehicles(List.of())
                .capitalDisregards(List.of())
                .legalAidApplicationTransactionTypes(List.of())
                .regularTransactions(List.of())
                .cashTransactions(List.of())
                .mostRecentCFESubmission(cfeSubmissionGenerator.createDefault())
                .build();
    }
}

