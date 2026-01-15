# Local Development with LocalStack for DynamoDB and S3
This setup provides a powerful and flexible local development experience, enabling developers to build and test features with high confidence before deploying to the cloud.

| **Version Skew:** The version of LocalStack might not support the latest AWS API features. | We will pin the LocalStack version and periodically review and update it as part of dependency management. |
| **Service Initialization:** DynamoDB tables and S3 buckets need to be created on startup. | An initialization script will be run by the Docker Compose setup to create the necessary resources when the LocalStack container starts. |
| **Data Persistence:** By default, the LocalStack container is ephemeral. | We will configure a Docker volume to persist the LocalStack state between restarts, preserving tables and data. |
| :--- | :--- |
| Issue | Mitigation |

## Potential Issues and Mitigations

| **Isolated Environments** | |
| **Simplified CI/CD** | |
| **Fast Feedback Loop** | **Maintenance:** The Docker Compose and initialization scripts require maintenance as the project evolves. |
| **Offline Development** | **Resource Intensive:** Running multiple services in Docker can consume significant local machine resources (RAM/CPU). |
| **Cost-Effective** | **Fidelity Gaps:** LocalStack is an emulation, not the real AWS. There can be minor differences in behavior or features. |
| :--- | :--- |
| Pros | Cons |

## Pros and Cons

This is managed in the `AwsConfig` class and configured in `application.yml`.

-   **Default profile (cloud):** When deployed to a cloud environment, the application will use the default credential provider chain to securely obtain credentials (e.g., via IRSA in Kubernetes).
-   **`local` profile:** When this profile is active, the application's AWS clients will be configured to point to the LocalStack endpoint (`http://localhost:4566`) and use static test credentials.

The application will be configured to switch between local and cloud environments seamlessly using Spring Profiles.

## Configuration

We will use Docker Compose to manage the LocalStack container alongside our application's database, ensuring a consistent and reproducible setup for all developers.

For rapid development and testing, it is crucial to have a local environment that closely mirrors the cloud services used in production. LocalStack allows us to run a local instance of AWS services, enabling end-to-end testing of our event-driven architecture without incurring AWS costs or requiring network connectivity.

## Overview

This document outlines the approach for setting up a local development environment using LocalStack to emulate AWS services like DynamoDB and S3.


