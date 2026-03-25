package uk.gov.justice.laa.dstew.access.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
public class OpenApiResourceController {

    @GetMapping(value = "/open-api-application-specification.yml", produces = "application/yaml")
    public String getOpenApiSpec() throws IOException {
        return loadYamlFile("open-api-application-specification.yml");
    }

  @GetMapping(value = "/open-api-specification.yml", produces = "application/yaml")
  public String getOpenApiBaseSpec() throws IOException {
    return loadYamlFile("open-api-specification.yml");
  }

    @GetMapping(value = "/common/{filename:.+}", produces = "application/yaml")
    public String getCommonSpec(@PathVariable String filename) throws IOException {
        return loadYamlFile("common/" + filename);
    }

    @GetMapping(value = "/v1/{filename:.+}", produces = "application/yaml")
    public String getV1Spec(@PathVariable String filename) throws IOException {
        return loadYamlFile("v1/" + filename);
    }

    @GetMapping(value = "/v2/{filename:.+}", produces = "application/yaml")
    public String getV2Spec(@PathVariable String filename) throws IOException {
        return loadYamlFile("v2/" + filename);
    }

    private String loadYamlFile(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        return Files.readString(resource.getFile().toPath(), StandardCharsets.UTF_8);
    }
}
