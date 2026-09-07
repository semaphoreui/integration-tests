package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.IntegrationExtractValueRequest;
import io.bookwright.api.model.semaphore.IntegrationMatcherRequest;
import io.bookwright.api.model.semaphore.IntegrationRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.util.TestData;
import java.util.Map;

/** Data for a local token-authenticated and matcher-routed webhook integration. */
public record SemaphoreIntegrationFixtures(
    String projectName,
    String integrationName,
    String templateName,
    String playbook,
    SemaphoreFixtures.SecretAccessKey authKey,
    String authHeader,
    String eventHeader,
    String acceptedEvent,
    String traceHeader,
    String traceValue,
    String releaseValue,
    String outputMarker) {

  public static SemaphoreIntegrationFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreIntegrationFixtures(
        "bookwright-webhook-project-" + suffix,
        "bookwright-webhook-" + suffix,
        "bookwright-webhook-template-" + suffix,
        "test-environment/fixtures/ansible/integration-webhook.yml",
        new SemaphoreFixtures.SecretAccessKey(
            "bookwright-webhook-token-" + suffix,
            "login_password",
            "bookwright-webhook",
            "Bw-webhook-" + suffix + "-42!"),
        "X-Bookwright-Token",
        "X-Bookwright-Event",
        "deploy",
        "X-Bookwright-Trace",
        "trace-2026-08",
        "2026.08",
        "semaphore-bookwright-webhook-ok");
  }

  public ProjectRequest projectRequest() {
    return new ProjectRequest(projectName, false, 0);
  }

  public TemplateRequest templateRequest(long projectId, long repositoryId, long inventoryId) {
    return new TemplateRequest(
        templateName, projectId, inventoryId, repositoryId, 0, playbook, "ansible", "");
  }

  public IntegrationRequest integrationRequest(long projectId, long templateId, long secretId) {
    return new IntegrationRequest(
        integrationName, projectId, templateId, "token", secretId, authHeader, true);
  }

  public IntegrationMatcherRequest matcherRequest(long integrationId) {
    return new IntegrationMatcherRequest(
        integrationId,
        "Route deploy event",
        "header",
        "equals",
        "string",
        eventHeader,
        acceptedEvent);
  }

  public IntegrationExtractValueRequest releaseExtractor(long integrationId) {
    return new IntegrationExtractValueRequest(
        integrationId,
        "Extract release",
        "body",
        "json",
        "payload.release",
        "webhook_release",
        "environment");
  }

  public IntegrationExtractValueRequest traceExtractor(long integrationId) {
    return new IntegrationExtractValueRequest(
        integrationId,
        "Extract trace",
        "header",
        "string",
        traceHeader,
        "webhook_trace",
        "environment");
  }

  public Map<String, Object> payload() {
    return Map.of("payload", Map.of("release", releaseValue));
  }

  public Map<String, String> acceptedHeaders() {
    return headers(authKey.password(), acceptedEvent);
  }

  public Map<String, String> invalidTokenHeaders() {
    return headers(authKey.password() + "-invalid", acceptedEvent);
  }

  public Map<String, String> unmatchedHeaders() {
    return headers(authKey.password(), "ignored");
  }

  private Map<String, String> headers(String token, String event) {
    return Map.of(authHeader, token, eventHeader, event, traceHeader, traceValue);
  }

  @Override
  public String toString() {
    return "SemaphoreIntegrationFixtures[project=%s, integration=%s, template=%s, auth=[REDACTED]]"
        .formatted(projectName, integrationName, templateName);
  }
}
