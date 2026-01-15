import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationContentFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationCreateRequestFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationUpdateRequestFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualFactory;

public class TestHarness {
  // Path to the file containing IDs (one per line)
  private static final String IDS_FILE = "/ids.txt"; // Classpath resource

  private final static ApplicationUpdateRequestFactory applicationUpdateRequestFactory = new ApplicationUpdateRequestFactory();
  private final static ApplicationCreateRequestFactory applicationCreateRequestFactory
      = new ApplicationCreateRequestFactory(new IndividualFactory(), new ApplicationContentFactory());
  // Base URL for the ApplicationController endpoints
  private static final String BASE_URL = "http://localhost:8080/api/v0/applications";

  public static void main(String[] args) throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    // Read IDs from classpath resource
    List<String> ids = new ArrayList<>();
//      ids = getIds();

    int numberIdsToGenerate = 1;
    for (int i = 0; i < numberIdsToGenerate; i++) {

      String newId = "LAA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
      ids.add(newId);
    }
//      List<String> randomIds = ids.stream()
//          .limit(5)
//          .collect(Collectors.toList());

    if (ids == null) {
      return;
    }

    // Create a list of ApplicationCreateRequest bodies as a collection
    List<ApplicationCreateRequest> createRequests = ids.stream()
        .parallel()
        .map(id -> applicationCreateRequestFactory.createDefault(
            builder -> builder.laaReference(id)
        ))
        .toList();

    // Stream through createRequests, make POST requests, and capture results
    System.out.println("\n========== Starting POST (Create) Operations ==========");
    long postStartTime = System.currentTimeMillis();

    List<PostResult> postResults = createRequests.stream()
        .parallel()
        .map(req -> {
          try {
            HttpResponse<String> response = getPostResponse(req, client);
            String location = response.headers().firstValue("Location").orElse(null);
            return new PostResult(req, response, location);
          } catch (Exception e) {
            System.err.println("POST failed: " + e.getMessage());
            return null;
          }
        })
        .filter(result -> result != null && result.location != null && result.location.contains("/"))
        .toList();

    long postEndTime = System.currentTimeMillis();
    long postDuration = postEndTime - postStartTime;
    System.out.println("========== POST Operations Completed ==========");
    System.out.println("Total POST requests: " + postResults.size());
    System.out.println("Total time: " + postDuration + " ms (" + String.format("%.2f", postDuration / 1000.0) + " seconds)");
    if (!postResults.isEmpty()) {
      double avgTimePerPost = (double) postDuration / postResults.size();
      double throughputPerSecond = postResults.size() / (postDuration / 1000.0);
      System.out.println("Average time per POST: " + String.format("%.2f", avgTimePerPost) + " ms");
      System.out.println("Throughput: " + String.format("%.0f", throughputPerSecond) + " requests/second");
    }
    System.out.println();

    // Create a list of update requests with their patch URLs
    List<UpdateRequestData> updateRequests = postResults.stream()
        .parallel()
        .map(result -> {
          String[] parts = result.location.split("/");
          String appId = parts[parts.length - 1];
          try {
            return UUID.fromString(appId); // Validate UUID
          } catch (Exception e) {
            System.out.println("Invalid UUID in Location header, skipping PATCH for id: " + appId);
            return null;
          }

        })
        .map(appId -> getUpdateRequestData(String.valueOf(appId), "Updated value for " + appId, 10))
        .flatMap(list -> list.stream())
        .toList();

    // Pass updateRequests to a method that performs PATCH calls
    System.out.println("========== Starting PATCH (Update) Operations ==========");
    long patchStartTime = System.currentTimeMillis();

    performPatchRequests(updateRequests, client);

    long patchEndTime = System.currentTimeMillis();
    long patchDuration = patchEndTime - patchStartTime;
    System.out.println("========== PATCH Operations Completed ==========");
    System.out.println("Total PATCH requests: " + updateRequests.size());
    System.out.println("Total time: " + patchDuration + " ms (" + String.format("%.2f", patchDuration / 1000.0) + " seconds)");
    if (!updateRequests.isEmpty()) {
      double avgTimePerPatch = (double) patchDuration / updateRequests.size();
      double throughputPerSecond = updateRequests.size() / (patchDuration / 1000.0);
      System.out.println("Average time per PATCH: " + String.format("%.2f", avgTimePerPatch) + " ms");
      System.out.println("Throughput: " + String.format("%.0f", throughputPerSecond) + " requests/second");
    }
    System.out.println();

    // Overall summary
    long totalDuration = postDuration + patchDuration;
    int totalOperations = postResults.size() + updateRequests.size();
    System.out.println("========== Overall Summary ==========");
    System.out.println("Total operations: " + totalOperations);
    System.out.println("POST operations time: " + postDuration + " ms (" + String.format("%.2f", postDuration / 1000.0) + " seconds)");
    System.out.println("PATCH operations time: " + patchDuration + " ms (" + String.format("%.2f", patchDuration / 1000.0) + " seconds)");
    System.out.println("Total execution time: " + totalDuration + " ms (" + String.format("%.2f", totalDuration / 1000.0) + " seconds)");
    if (totalOperations > 0) {
      double overallThroughput = totalOperations / (totalDuration / 1000.0);
      System.out.println("Overall throughput: " + String.format("%.0f", overallThroughput) + " requests/second");
    }
  }

  private static @NonNull List<UpdateRequestData> getUpdateRequestData(String appId, String newValue, int numberOfUpdates) {
    List<UpdateRequestData> updateRequests = new ArrayList<>();
    for (int i = 1; i <= numberOfUpdates; i++) {

      String patchUrl = BASE_URL + "/" + appId;
      ApplicationUpdateRequest updateRequest = applicationUpdateRequestFactory.createDefault(
          builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS)
              .applicationContent(Map.of("updatedField", newValue)
              ));
      updateRequests.add(new UpdateRequestData(patchUrl, updateRequest));
    }

    return updateRequests;
  }

  private static @NonNull HttpResponse<String> getPostResponse(ApplicationCreateRequest createRequest, HttpClient client)
      throws IOException, InterruptedException {
    String postBody = MapperUtil.getObjectMapper().writeValueAsString(createRequest);
    String postUrl = BASE_URL;
    HttpRequest postRequest = HttpRequest.newBuilder()
        .uri(URI.create(postUrl))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(postBody))
        .build();
    HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
    return postResponse;
  }

  // Helper class to hold POST results
  private static class PostResult {
    ApplicationCreateRequest request;
    HttpResponse<String> response;
    String location;

    PostResult(ApplicationCreateRequest request, HttpResponse<String> response, String location) {
      this.request = request;
      this.response = response;
      this.location = location;
    }
  }

  // Helper class to hold PATCH request data
  private static class UpdateRequestData {
    String patchUrl;
    ApplicationUpdateRequest updateRequest;

    UpdateRequestData(String patchUrl, ApplicationUpdateRequest updateRequest) {
      this.patchUrl = patchUrl;
      this.updateRequest = updateRequest;
    }
  }

  // Method to perform PATCH requests
  private static void performPatchRequests(List<UpdateRequestData> updateRequests, HttpClient client) {
    updateRequests.stream()
        .parallel()
        .forEach(data -> {
          try {
            String patchBody = MapperUtil.getObjectMapper().writeValueAsString(data.updateRequest);
            HttpRequest patchRequest = HttpRequest.newBuilder()
                .uri(URI.create(data.patchUrl))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(patchBody))
                .build();
            HttpResponse<String> patchResponse = client.send(patchRequest, HttpResponse.BodyHandlers.ofString());
//        System.out.println("PATCH " + data.patchUrl + " => " + patchResponse.statusCode());
//        System.out.println(patchResponse.body());
          } catch (Exception e) {
            System.err.println("PATCH failed for URL " + data.patchUrl + ": " + e.getMessage());
          }
        });
  }

  private static @Nullable List<String> getIds() {
    List<String> ids;
    try (InputStream is = TestHarness.class.getResourceAsStream(IDS_FILE)) {
      if (is == null) {
        System.err.println("Failed to find IDs file on classpath: " + IDS_FILE);
        return null;
      }
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
        ids = reader.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .toList();
      }
    } catch (Exception e) {
      System.err.println("Failed to read IDs from classpath resource: " + IDS_FILE);
      e.printStackTrace();
      return null;
    }
    if (ids.isEmpty()) {
      System.err.println("No IDs found in file: " + IDS_FILE);
      return null;
    }
    return ids;
  }
}
