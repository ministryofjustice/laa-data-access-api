package uk.gov.justice.laa.dstew.access.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uk.gov.justice.laa.dstew.access.config.devlopment.LocalStackResourceInitializer;

public class LocalStackTestUtility {
    private static final Logger log = LoggerFactory.getLogger(LocalStackTestUtility.class);

//    private final DynamoDbClient dynamoDbClient;
//    private final S3Client s3Client;
//
//    public LocalStackTestUtility(DynamoDbClient dynamoDbClient, S3Client s3Client) {
//        this.dynamoDbClient = dynamoDbClient;
//        this.s3Client = s3Client;
//    }

    public void createTableWithGsi(DynamoDbClient dynamoDbClient) {
        String tableName = "events";
        if (dynamoDbClient.listTables().tableNames().contains(tableName)) {
            dynamoDbClient.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
        }

        CreateTableRequest request = LocalStackResourceInitializer
                .getCreateTableRequest(tableName);

        try {
            dynamoDbClient.createTable(request);
        } catch (ResourceInUseException e) {
            // Table already exists - make creation idempotent so tests can run against existing LocalStack
            // (for example when Testcontainers can't create containers and we fallback to an existing compose-managed LocalStack).
            // Continue to waiting for the table to be ACTIVE below.
        }

        // wait until ACTIVE
        waitForTableActive(dynamoDbClient, tableName);
    }

    private void waitForTableActive(DynamoDbClient dynamoDbClient, String tableName) {
        for (int i = 0; i < 30; i++) {
            try {
                DescribeTableResponse resp = dynamoDbClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
                if (resp.table().tableStatus() == TableStatus.ACTIVE) {
                    return;
                }
                Thread.sleep(500);
            } catch (ResourceNotFoundException rnfe) {
                // continue waiting
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Table did not become active in time");
    }

    public void createBucket(S3Client s3Client) {
        String bucketName = "test-bucket";
        int attempts = 0;
        int maxAttempts = 30;
        while (true) {
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                return;
            } catch (S3Exception s3e) {
                // If the bucket already exists, consider creation successful
                try {
                    ListBucketsResponse list = s3Client.listBuckets();
                    boolean exists = list.buckets().stream().anyMatch(b -> bucketName.equals(b.name()));
                    if (exists) {
                        return;
                    }
                } catch (Exception ignored) {
                    // ignore and fall through to retry
                }

                attempts++;
                if (attempts >= maxAttempts) {
                    throw new RuntimeException("Failed to create S3 bucket after retries", s3e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            } catch (Exception e) {
                // Non-S3 exceptions: attempt to detect if the bucket exists (fallback) then retry
                try {
                    ListBucketsResponse list = s3Client.listBuckets();
                    boolean exists = list.buckets().stream().anyMatch(b -> bucketName.equals(b.name()));
                    if (exists) {
                        return;
                    }
                } catch (Exception ignored) {
                    // ignore and fall through to retry
                }

                attempts++;
                if (attempts >= maxAttempts) {
                    throw new RuntimeException("Failed to create S3 bucket after retries", e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }
}
