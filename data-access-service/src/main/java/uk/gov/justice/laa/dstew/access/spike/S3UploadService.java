package uk.gov.justice.laa.dstew.access.spike;

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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for uploading files to S3.
 */
@Service
public class S3UploadService {

  private static final Logger logger = LoggerFactory.getLogger(S3UploadService.class);
  private final S3Client s3Client;
  private final ObjectMapper objectMapper;

    public S3UploadService(S3Client s3Client, ObjectMapper objectMapper) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
    }

  public String downloadUrl(String bucketName, String key) {
    Objects.requireNonNull(bucketName, "bucketName must not be null");
    Objects.requireNonNull(key, "key must not be null");
    return formatS3Uri(bucketName, key);
  }

    /**
     * Format an S3 URI for the given bucket and key: s3://bucket/key
     */
    private String formatS3Uri(String bucketName, String key) {
        return String.format("s3://%s/%s", bucketName, key);
    }

    /**
     * Download object content as a UTF-8 string.
     * Returns null if the object cannot be read.
     */
    public String downloadObjectAsString(String bucketName, String key) {
         Objects.requireNonNull(bucketName, "bucketName must not be null");
         Objects.requireNonNull(key, "key must not be null");

         GetObjectRequest req = GetObjectRequest.builder()
             .bucket(bucketName)
             .key(key)
             .build();

         ResponseInputStream<GetObjectResponse> resp = null;
         try {
             resp = s3Client.getObject(req);
             return readResponseToString(resp);
         } catch (S3Exception e) {
             logger.error("S3 getObject failed for s3://{}/{}: {}", bucketName, key,
                     e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorMessage(), e);
             return null;
         } finally {
             if (resp != null) {
                 try {
                     resp.close();
                 } catch (IOException ioe) {
                     logger.warn("Failed to close S3 response stream for s3://{}/{}: {}", bucketName, key, ioe.getMessage());
                 }
             }
         }
     }

    /**
     * Read the full ResponseInputStream<GetObjectResponse> into a UTF-8 string.
     * This method closes the stream.
     */
    private String readResponseToString(ResponseInputStream<GetObjectResponse> resp) {
        if (resp == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int read;
            while ((read = resp.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
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
            String eTag = response.eTag();
            String url = String.format("s3://%s/%s", bucketName, key);

            logger.debug("Uploaded object to {} (eTag={}, success={})", url, eTag, success);

            return new S3UploadResult(bucketName, key, eTag, success, url);
        } catch (S3Exception e) {
            logger.error("S3 upload failed for s3://{}/{}: {}", bucketName, key, e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorMessage(), e);
            return new S3UploadResult(bucketName, key, null, false, String.format("s3://%s/%s", bucketName, key));
        }
    }

    /**
     * Uploads from an InputStream when the content length is known.
     * Useful for streaming large payloads.
     *
     * @param inputStream input stream to upload
     * @param contentLength number of bytes in the stream
     * @param bucketName target bucket
     * @param key object key
     * @return S3UploadResult with eTag, url and success flag
     */
    public S3UploadResult upload(InputStream inputStream, long contentLength, String bucketName, String key) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(bucketName, "bucketName must not be null");
        Objects.requireNonNull(key, "key must not be null");

        try {
            // Read the stream into a byte[] to avoid chunked SigV4 streaming bodies which some S3-compatible servers
            // (e.g. certain LocalStack versions) fail to parse. This trades memory for compatibility.
            byte[] bytes = toBytes(inputStream, contentLength);
            return upload(bytes, bucketName, key);
        } catch (S3Exception e) {
            logger.error("S3 upload failed for s3://{}/{}: {}", bucketName, key,
                    e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorMessage(), e);
            return new S3UploadResult(bucketName, key, null, false, String.format("s3://%s/%s", bucketName, key));
        } catch (Exception e) {
            logger.error("Failed to read input stream for upload to s3://{}/{}: {}", bucketName, key, e.getMessage(), e);
            return new S3UploadResult(bucketName, key, null, false, String.format("s3://%s/%s", bucketName, key));
        }
    }

    /**
     * Read an InputStream fully into a byte[].
     * If expectedLength is > 0 and <= Integer.MAX_VALUE the ByteArrayOutputStream will be pre-sized.
     */
    private byte[] toBytes(InputStream inStream, long expectedLength) throws IOException {
        if (inStream == null) {
            throw new IllegalArgumentException("input stream must not be null");
        }

        int initialSize = (expectedLength > 0 && expectedLength <= Integer.MAX_VALUE) ? (int) expectedLength : 0;
        try (InputStream in = inStream; ByteArrayOutputStream out = initialSize > 0 ? new ByteArrayOutputStream(initialSize) : new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
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
     *
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

    private byte[] toBytes(Object payload) throws IOException {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }

        if (payload instanceof byte[]) {
            return (byte[]) payload;
        }

        if (payload instanceof String) {
            return ((String) payload).getBytes(StandardCharsets.UTF_8);
        }

        if (payload instanceof InputStream) {
            // use the stream helper without an expected length
            return toBytes((InputStream) payload, -1);
        }

        if (payload instanceof File) {
            return Files.readAllBytes(((File) payload).toPath());
        }

        if (payload instanceof Path) {
            return Files.readAllBytes((Path) payload);
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
            // last-resort fallback: toString()
        }

         // Fallback to toString()
         return payload.toString().getBytes(StandardCharsets.UTF_8);
     }

}
