package uk.gov.justice.laa.dstew.access.utils;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import uk.gov.justice.laa.dstew.access.Constants;

public class LocalstackContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

//    private static final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>(Constants.POSTGRES_INSTANCE);
  private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:3.2.0");
  private static final Region AWS_REGION = Region.EU_WEST_2;

  private static final LocalStackContainer localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
      .withServices(S3, DYNAMODB)
      .withEnv("DEFAULT_REGION", AWS_REGION.toString());
    static {
        localstack.start();
    }


    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
      TestPropertyValues.of(
              "cloud.aws.region.static=" + AWS_REGION.toString(),
              "cloud.aws.credentials.access-key=test",
              "cloud.aws.credentials.secret-key=test",
              "cloud.aws.stack-name=localstack",
              "cloud.aws.endpoint-override=" + localstack.getEndpointOverride(DYNAMODB).toString()
      ).applyTo(applicationContext.getEnvironment());
    }

    public  static LocalStackContainer getLocalstack() {
        return localstack;
    }
}

