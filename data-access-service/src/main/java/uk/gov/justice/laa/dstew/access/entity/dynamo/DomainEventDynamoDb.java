package uk.gov.justice.laa.dstew.access.entity.dynamo;

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

/**
 * DynamoDB entity representing a domain event in the application.
 * This class is annotated for use with the AWS SDK's DynamoDB Enhanced Client.
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
@Data
public class DomainEventDynamoDb {

  private String type;
  private String description;
  private String createdAt;
  private String s3location;
  private String caseworkerId;
  private String applicationId;

  @DynamoDbSecondaryPartitionKey(indexNames = "gs-index-1")
  @DynamoDbAttribute("gs1pk")
  public String getGs1pk() {
    return (caseworkerId != null ? "CASEWORKER#" + caseworkerId : null);
  }

  public void setGs1pk(String gs1pk) {
    // No-op: value is computed from caseworkerId
  }


  @DynamoDbSecondarySortKey(indexNames = "gs-index-1")
  @DynamoDbAttribute("gs1sk")
  public String getGs1sk() {
    return getPk();
  }

  public void setGs1sk(String gs1sk) {
    // No-op: value is computed from pk
  }

  @DynamoDbSecondaryPartitionKey(indexNames = "gs-index-2")
  @DynamoDbAttribute("gs2pk")
  public String getGs2pk() {
    return getPk();
  }

  public void setGs2pk(String gs2pk) {
    // No-op: value is computed from applicationId (same as pk)
  }

  @DynamoDbSecondarySortKey(indexNames = "gs-index-2")
  @DynamoDbAttribute("gs2sk")
  public String getGs2sk() {
    return createdAt != null ? createdAt : "";
  }

  public void setGs2sk(String gs2sk) {
    // No-op: value is computed from createdAt
  }

  @DynamoDbPartitionKey
  @DynamoDbAttribute("pk")
  public String getPk() {
    return "application#" + (applicationId != null ? applicationId : "");
  }

  public void setPk(String pk) {
    // No-op: value is computed from applicationId
  }

  @DynamoDbSortKey
  @DynamoDbAttribute("sk")
  public String getSk() {
    return (type != null ? type : "#") + (createdAt != null ? createdAt : "");
  }

  public void setSk(String sk) {
    // No-op: value is computed from type and createdAt
  }


}
