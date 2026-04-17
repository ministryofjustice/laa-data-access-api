package uk.gov.justice.laa.dstew.access.massgenerator.model;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullMeans {
  private Map<String, Object> openBanking;
  private String otherAssetsDeclaration;
  private String savingsAmount;
  private List<Object> dependants;
  private List<Object> vehicles;
  private List<Object> capitalDisregards;
  private List<Object> legalAidApplicationTransactionTypes;
  private List<Object> regularTransactions;
  private List<Object> cashTransactions;
  private FullCfeSubmission mostRecentCFESubmission;
}

