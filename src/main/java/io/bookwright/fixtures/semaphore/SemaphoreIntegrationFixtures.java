package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.Integration;
import io.bookwright.api.model.semaphore.IntegrationExtractValueRequest;
import io.bookwright.api.model.semaphore.IntegrationMatcherRequest;
import io.bookwright.api.model.semaphore.IntegrationRequest;
import io.bookwright.api.model.semaphore.IntegrationUpdateRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.api.model.semaphore.WebhookHeaders;
import io.bookwright.util.TestData;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Data for local authenticated and matcher-routed webhook integrations. */
public record SemaphoreIntegrationFixtures(
    String projectName,
    String integrationName,
    String templateName,
    String playbook,
    SemaphoreFixtures.SecretAccessKey authKey,
    String authHeader,
    String hmacHeader,
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
        "ansible/integration-webhook.yml",
        new SemaphoreFixtures.SecretAccessKey(
            "bookwright-webhook-token-" + suffix,
            "login_password",
            "bookwright-webhook",
            "Bw-webhook-" + suffix + "-42!"),
        "X-Bookwright-Token",
        "X-Bookwright-Signature",
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
    return integrationRequest(projectId, templateId, secretId, tokenAuthentication());
  }

  public IntegrationRequest integrationRequest(
      long projectId, long templateId, long secretId, WebhookAuthentication authentication) {
    return new IntegrationRequest(
        integrationName + "-" + authentication.method(),
        projectId,
        templateId,
        authentication.method(),
        secretId,
        authentication.authHeader(),
        true);
  }

  public IntegrationUpdateRequest updatedIntegration(Integration integration) {
    return new IntegrationUpdateRequest(
        integration.id(),
        updatedIntegrationName(),
        integration.projectId(),
        integration.templateId(),
        integration.authMethod(),
        integration.authSecretId(),
        integration.authHeader(),
        false);
  }

  public String updatedIntegrationName() {
    return integrationName + "-updated";
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

  public IntegrationMatcherRequest updatedMatcher(long integrationId) {
    return new IntegrationMatcherRequest(
        integrationId,
        "Route updated deploy event",
        "header",
        "equals",
        "string",
        eventHeader,
        acceptedEvent + "-updated");
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

  public IntegrationExtractValueRequest updatedReleaseExtractor(long integrationId) {
    return new IntegrationExtractValueRequest(
        integrationId,
        updatedReleaseExtractorName(),
        "body",
        "json",
        "payload.release",
        updatedReleaseVariable(),
        "environment");
  }

  public String updatedReleaseExtractorName() {
    return "Extract updated release";
  }

  public String updatedReleaseVariable() {
    return "webhook_release_updated";
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

  public String payloadJson() {
    return "{\"payload\":{\"release\":\"%s\"}}".formatted(releaseValue);
  }

  public WebhookHeaders acceptedHeaders() {
    return tokenAuthentication().acceptedHeaders();
  }

  public WebhookHeaders invalidTokenHeaders() {
    return tokenAuthentication().rejectedHeaders();
  }

  public WebhookHeaders unmatchedHeaders() {
    return headers(Map.of(authHeader, authKey.password()), "ignored");
  }

  public WebhookAuthentication tokenAuthentication() {
    return authentication(
        "token",
        authHeader,
        Map.of(authHeader, authKey.password()),
        Map.of(authHeader, authKey.password() + "-invalid"));
  }

  public WebhookAuthentication githubAuthentication() {
    return signedAuthentication("github", "X-Hub-Signature-256", "sha256=");
  }

  public WebhookAuthentication hmacAuthentication() {
    return signedAuthentication("hmac", hmacHeader, "", "HmacSHA256");
  }

  public WebhookAuthentication hmacSha512Authentication() {
    return signedAuthentication("hmac-sha512", hmacHeader, "", "HmacSHA512");
  }

  public WebhookAuthentication bitbucketAuthentication() {
    return signedAuthentication("bitbucket", "X-Hub-Signature", "sha256=");
  }

  public WebhookAuthentication basicAuthentication() {
    return authentication(
        "basic",
        "Authorization",
        Map.of("Authorization", basic(authKey.login(), authKey.password())),
        Map.of("Authorization", basic(authKey.login(), authKey.password() + "-invalid")));
  }

  private WebhookAuthentication signedAuthentication(
      String method, String signatureHeader, String prefix) {
    return signedAuthentication(method, signatureHeader, prefix, "HmacSHA256");
  }

  private WebhookAuthentication signedAuthentication(
      String method, String signatureHeader, String prefix, String algorithm) {
    return authentication(
        method,
        signatureHeader,
        Map.of(signatureHeader, prefix + hmac(algorithm, authKey.password(), payloadJson())),
        Map.of(signatureHeader, prefix + hmac(algorithm, authKey.password(), payloadJson() + " ")));
  }

  private WebhookAuthentication authentication(
      String method,
      String authenticationHeader,
      Map<String, String> acceptedAuthentication,
      Map<String, String> rejectedAuthentication) {
    return new WebhookAuthentication(
        method,
        authenticationHeader,
        headers(acceptedAuthentication, acceptedEvent),
        headers(rejectedAuthentication, acceptedEvent));
  }

  private WebhookHeaders headers(Map<String, String> authentication, String event) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(eventHeader, event);
    headers.put(traceHeader, traceValue);
    headers.putAll(authentication);
    return new WebhookHeaders(headers);
  }

  private String basic(String login, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((login + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  private String hmac(String algorithm, String secret, String payload) {
    try {
      Mac hmac = Mac.getInstance(algorithm);
      hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
      return HexFormat.of().formatHex(hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException error) {
      throw new IllegalStateException("%s is not available".formatted(algorithm), error);
    }
  }

  public record WebhookAuthentication(
      String method,
      String authHeader,
      WebhookHeaders acceptedHeaders,
      WebhookHeaders rejectedHeaders) {

    @Override
    public String toString() {
      return "WebhookAuthentication[method=%s, authHeader=%s, credentials=[REDACTED]]"
          .formatted(method, authHeader);
    }
  }

  @Override
  public String toString() {
    return "SemaphoreIntegrationFixtures[project=%s, integration=%s, template=%s, auth=[REDACTED]]"
        .formatted(projectName, integrationName, templateName);
  }
}
