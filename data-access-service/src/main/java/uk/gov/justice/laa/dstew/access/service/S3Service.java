package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uk.gov.justice.laa.dstew.access.model.S3UploadResult;


/**
 * Service for uploading files to S3.
 */
@Service
public class S3Service {

  private static final Logger logger = LoggerFactory.getLogger(S3Service.class);
  private final S3Client s3Client;
  private final ObjectMapper objectMapper;

  public S3Service(S3Client s3Client, ObjectMapper objectMapper) {
    this.s3Client = s3Client;
    this.objectMapper = objectMapper;
  }

  /**
   * Download object content as a UTF-8 string.
   * Returns null if the object cannot be read.
   */
  private String downloadObjectAsString(String bucketName, String key) {
    Objects.requireNonNull(bucketName, "bucketName must not be null");
    Objects.requireNonNull(key, "key must not be null");

    GetObjectRequest req = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();

    try (ResponseInputStream<GetObjectResponse> resp = s3Client.getObject(req)) {
      return readResponseToString(resp);
    } catch (S3Exception e) {
      logger.error("S3 getObject failed for s3://{}/{}: {}", bucketName, key,
          e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorMessage(), e);
      return null;
    } catch (IOException e) {
      logger.error("Failed to read S3 object stream for s3://{}/{}: {}", bucketName, key, e.getMessage(), e);
      return null;
    }
  }

  /**
   * Read the full ResponseInputStream GetObjectResponse  into a UTF-8 string.
   * This method closes the stream.
   */
  public String readResponseToString(ResponseInputStream<GetObjectResponse> resp) {
    if (resp == null) {
      return null;
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      byte[] buf = new byte[8192];
      int read;
      while ((read = resp.read(buf)) != -1) {
        out.write(buf, 0, read);
      }
      return out.toString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      logger.error("Failed to read S3 object stream: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * Uploads a byte[] payload to S3 and returns an upload result containing success metadata.
   *
   * @param payload    bytes to upload
   * @param bucketName target bucket
   * @param key        object key
   * @return S3UploadResult with eTag, url and success flag
   */
  public S3UploadResult upload(byte[] payload, String bucketName, String key) {
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(bucketName, "bucketName must not be null");
    Objects.requireNonNull(key, "key must not be null");

    try {
      PutObjectRequest request = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(key).contentType("application/json")
          .build();


      PutObjectResponse response = s3Client.putObject(request, RequestBody.fromBytes(payload));

      boolean success = response.sdkHttpResponse() != null && response.sdkHttpResponse().isSuccessful();
      String etag = response.eTag();
      String url = String.format("s3://%s/%s", bucketName, key);

      logger.debug("Uploaded object to {} (eTag={}, success={})", url, etag, success);

      return new S3UploadResult(bucketName, key, etag, success, url);
    } catch (S3Exception e) {
      logger.error("S3 upload failed for s3://{}/{}: {}", bucketName, key,
          e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorMessage(), e);
      return new S3UploadResult(bucketName, key, null, false, String.format("s3://%s/%s", bucketName, key));
    }
  }

  /**
   * Uploads a generic Object payload to S3. Supported payload types:
   * - byte[]
   * - String (UTF-8)
   * - InputStream (will be fully read into memory)
   * - File / Path
   * - Serializable objects (Java serialization)
   * - fallback to payload.toString() as UTF-8
   * Be careful with very large streams/objects as this method buffers into memory.
   */
  public S3UploadResult upload(Object payload, String bucketName, String key) {
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(bucketName, "bucketName must not be null");
    Objects.requireNonNull(key, "key must not be null");

    try {
      byte[] bytes = toBytes(payload);
      return upload(bytes, bucketName, key);
    } catch (Exception e) {
      logger.error("Failed to convert payload to bytes for upload to s3://{}/{}: {}", bucketName, key, e.getMessage(), e);
      return new S3UploadResult(bucketName, key, null, false, String.format("s3://%s/%s", bucketName, key));
    }
  }

  /**
   * Read an InputStream fully into a byte[].
   * If expectedLength is > 0 and <= Integer.MAX_VALUE the ByteArrayOutputStream will be pre-sized.
   */
  private byte[] toBytes(InputStream inStream) throws IOException {
    if (inStream == null) {
      throw new IllegalArgumentException("input stream must not be null");
    }
    try (InputStream in = inStream;
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    }
  }


  private byte[] toBytes(Object payload) throws IOException {

    if (payload instanceof byte[]) {
      return (byte[]) payload;
    }

    if (payload instanceof String) {
      return ((String) payload).getBytes(StandardCharsets.UTF_8);
    }

    if (payload instanceof InputStream) {
      // use the stream helper without an expected length
      return toBytes((InputStream) payload);
    }


    // Prefer JSON serialization for Maps, Collections and POJOs to produce textual payloads that
    // are less likely to confuse S3-compatible servers' parsers (LocalStack historically chokes on
    // Java-serialized binary payloads interleaved with chunk-signature tokens).
    try {
      return objectMapper.writeValueAsBytes(payload);
    } catch (Exception jsonEx) {
      // Fallback to Java serialization if JSON serialization fails
      if (payload instanceof Serializable) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(baos)) {
          oos.writeObject(payload);
          oos.flush();
          return baos.toByteArray();
        }
      }
    }

    // Fallback to toString()
    return payload.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Convenience method to download S3 object content as a string using an s3://bucket/key URL.
   *
   * @param s3Url S3 URL in the format s3://bucket/key
   * @return object content as a UTF-8 string, or null if the object cannot be read
   * @throws IllegalArgumentException if the s3Url is null or not in the correct format
   */
  public String downloadEventsAsStrings(String s3Url) {
    s3Url = Objects.requireNonNull(s3Url, "s3Url must not be null");
    if (!s3Url.startsWith("s3://")) {
      throw new IllegalArgumentException("s3Url must start with s3://");
    }
    String path = s3Url.substring("s3://".length());
    int slashIndex = path.indexOf('/');
    if (slashIndex <= 0 || slashIndex == path.length() - 1) {
      throw new IllegalArgumentException("s3Url must be in the format s3://bucket/key");
    }
    String bucket = path.substring(0, slashIndex);
    String key = path.substring(slashIndex + 1);
    return downloadObjectAsString(bucket, key);
  }
}
