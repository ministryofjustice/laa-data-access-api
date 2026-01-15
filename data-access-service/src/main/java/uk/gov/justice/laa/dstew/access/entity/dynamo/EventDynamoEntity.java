package uk.gov.justice.laa.dstew.access.entity.dynamo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
@Data
public class EventDynamoEntity {

  private String pk;
  private String sk;
  private String type;
  private String description;
  private String createdAt;
  private String s3location;
  private String caseworkerId;
  private String applicationId;

  @DynamoDbSecondaryPartitionKey(indexNames = "caseworkerId-index")
  public String getGsiPk() {
    return caseworkerId != null ? "CASEWORKER#" + caseworkerId : null;
  }

  public void setGsiPk(String gsiPk) {
    // No-op: gsiPk is derived from caseworkerId
  }

  @DynamoDbSecondarySortKey(indexNames = "caseworkerId-index")
  public String getGsiSk() {
    return pk;
  }

  public void setGsiSk(String gsiSk) {
    // No-op: gsiSk is derived from pk and sk
  }

  @DynamoDbPartitionKey
  public String getPk() {
    return pk;
  }

  public void setPk(String pk) {
    this.pk = pk;
  }

  @DynamoDbSortKey
  public String getSk() {
    return sk;
  }

  public void setSk(String sk) {
    this.sk = sk;
  }




}
