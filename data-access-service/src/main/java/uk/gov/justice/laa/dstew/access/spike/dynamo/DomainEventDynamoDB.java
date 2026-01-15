package uk.gov.justice.laa.dstew.access.spike.dynamo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
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
public class DomainEventDynamoDB {

  private String pk;
  private String sk;
  private String type;
  private String description;
  private String createdAt;
  private String s3location;
  private String caseworkerId;
  private String applicationId;

  @DynamoDbSecondaryPartitionKey(indexNames = "gs-index-1")
  @DynamoDbAttribute("gs1pk")
  public String getGs1pk() {
    return caseworkerId != null ? "CASEWORKER#" + caseworkerId : null;
  }

  public void setGs1pk(String gs1pk) {
    // No-op: value is computed from caseworkerId
  }


  @DynamoDbSecondarySortKey(indexNames = "gs-index-1")
  @DynamoDbAttribute("gs1sk")
  public String getGs1sk() {
    return pk;
  }

  public void setGs1sk(String gs1sk) {
    // No-op: value is computed from pk
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
