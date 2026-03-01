package uk.gov.justice.laa.dstew.access.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.model.S3UploadResult;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3ServiceTest {

  @Mock
  private S3Client s3Client = Mockito.mock(S3Client.class);


  private final S3Service serviceUnderTest = new S3Service(s3Client, MapperUtil.getObjectMapper());

  @BeforeEach
  void setup() {
    Mockito.reset(s3Client);
  }

  @ParameterizedTest
  @MethodSource("getUploadTestCases")
  void testUpload_success(Object payload) {
    SdkHttpFullResponse sdkHttpFullResponse = SdkHttpResponse.builder().statusCode(200).build();
    PutObjectResponse putObjectResponse = (PutObjectResponse) PutObjectResponse
        .builder()
        .eTag("some-etag")
        .sdkHttpResponse(sdkHttpFullResponse)
        .build();
    Mockito.when(s3Client.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class)))
        .thenReturn(putObjectResponse);
    S3UploadResult upload = serviceUnderTest.upload(payload, "bucket-name", "object-key");
    Mockito.verify(s3Client).putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class));
    Assertions.assertTrue(upload.isSuccess());

  }

  private Stream<Arguments> getUploadTestCases() {
    return Stream.of(
        Arguments.of(Map.of("key1", "value1")),
        Arguments.of("test string"),
        Arguments.of("test string as bytes".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void testUpload_fail_500_response() {
    Mockito.when(s3Client.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class)))
        .thenThrow(S3Exception.class);
    S3UploadResult upload = serviceUnderTest.upload(Map.of("some-key", "some-value"), "bucket-name", "object-key");

    Mockito.verify(s3Client).putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class));
    Assertions.assertFalse(upload.isSuccess());
  }

  @Test
  void testUpload_fail_convert_payload_error() {
    S3UploadResult upload = serviceUnderTest.upload(InputStream.nullInputStream(), "bucket-name", "object-key");

    Mockito.verify(s3Client).putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class));
    Assertions.assertFalse(upload.isSuccess());
  }

  @Test
  void testDownload_success() {
    String expectedContent = "test event data";
    GetObjectRequest req = GetObjectRequest.builder()
        .bucket("validbucket")
        .key("validkey")
        .build();
    GetObjectResponse response = (GetObjectResponse) GetObjectResponse
        .builder()
        .sdkHttpResponse(SdkHttpResponse
            .builder()
            .statusCode(200)
            .build())
        .build();
    InputStream testInputStream = new ByteArrayInputStream(expectedContent.getBytes(StandardCharsets.UTF_8));
    ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(response, testInputStream);
    Mockito.when(s3Client.getObject(req)).thenReturn(responseInputStream);
    String download = serviceUnderTest.downloadEventsAsStrings("s3://validbucket/validkey");
    Mockito.verify(s3Client).getObject(req);
    Assertions.assertNotNull(download);
    Assertions.assertEquals(expectedContent, download);
  }

  @Test
  void testDownload_invalidUrl_noPrefix() {
    IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
        serviceUnderTest.downloadEventsAsStrings("bucket/key")
    );
    Assertions.assertTrue(ex.getMessage().contains("s3Url must start with s3://"));
  }

  @Test
  void testDownload_invalidUrl_missingKey() {
    IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
        serviceUnderTest.downloadEventsAsStrings("s3://bucketonly/")
    );
    Assertions.assertTrue(ex.getMessage().contains("s3Url must be in the format s3://bucket/key"));
  }

  @Test
  void testDownload_invalidUrl_missingBucket() {
    IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
        serviceUnderTest.downloadEventsAsStrings("s3:///keyonly")
    );
    Assertions.assertTrue(ex.getMessage().contains("s3Url must be in the format s3://bucket/key"));
  }

  @Test
  void testDownload_nullUrl() {
    Assertions.assertThrows(NullPointerException.class, () ->
        serviceUnderTest.downloadEventsAsStrings(null)
    );
  }

  @Test
  void testDownload_s3Exception() {
    GetObjectRequest req = GetObjectRequest.builder()
        .bucket("bucket")
        .key("key")
        .build();
    Mockito.when(s3Client.getObject(req)).thenThrow(S3Exception.builder().message("Access Denied").build());
    String result = serviceUnderTest.downloadEventsAsStrings("s3://bucket/key");
    Assertions.assertNull(result);
  }

  @Test
  void testDownload_ioException() {
    GetObjectRequest req = GetObjectRequest.builder()
        .bucket("bucket")
        .key("key")
        .build();
    // Custom InputStream that throws IOException on read
    InputStream brokenStream = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("Simulated IO error");
      }
    };
    GetObjectResponse response =
        (GetObjectResponse) GetObjectResponse.builder().sdkHttpResponse(SdkHttpResponse.builder().statusCode(200).build())
            .build();
    ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(response, brokenStream);
    Mockito.when(s3Client.getObject(req)).thenReturn(responseInputStream);
    String result = serviceUnderTest.downloadEventsAsStrings("s3://bucket/key");
    Assertions.assertNull(result);
  }

  static class UnserializableToJson implements java.io.Serializable {
    // Self-reference to break Jackson
    UnserializableToJson self = this;
  }

  @Test
  void testUpload_jsonSerializationFails_fallbackToJavaSerialization() {
    SdkHttpFullResponse sdkHttpFullResponse = SdkHttpResponse.builder().statusCode(200).build();
    PutObjectResponse putObjectResponse = (PutObjectResponse) PutObjectResponse
        .builder()
        .eTag("some-etag")
        .sdkHttpResponse(sdkHttpFullResponse)
        .build();
    Mockito.when(s3Client.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class)))
        .thenReturn(putObjectResponse);
    UnserializableToJson payload = new UnserializableToJson();
    S3UploadResult upload = serviceUnderTest.upload(payload, "bucket-name", "object-key");
    Mockito.verify(s3Client).putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class));
    Assertions.assertTrue(upload.isSuccess());
  }

}