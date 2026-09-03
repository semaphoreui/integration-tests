package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.api.model.semaphore.VariableGroup;
import io.bookwright.api.model.semaphore.VariableGroupRequest;
import io.bookwright.api.model.semaphore.VariableGroupSecret;
import io.bookwright.api.model.semaphore.VariableGroupSecretRequest;
import io.bookwright.util.TestData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Mixed plain/secret Variable Group data with safe proof hashes for task execution. */
public record SemaphoreVariableGroupFixtures(
    String name,
    String renamedVariable,
    String json,
    String env,
    Secret variableSecret,
    Secret environmentSecret,
    String templateName,
    String playbook,
    String outputMarker,
    String expectedValidationError) {

  public static SemaphoreVariableGroupFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    Secret variableSecret = new Secret("var", "db_password", "bookwright-db-password-" + suffix);
    Secret environmentSecret = new Secret("env", "API_TOKEN", "bookwright-api-token-" + suffix);
    return new SemaphoreVariableGroupFixtures(
        "bookwright-variable-group-" + suffix,
        "db_password_renamed",
        "{\"region\":\"eu-west-1\",\"expected_db_password_hash\":\"%s\"}"
            .formatted(sha256(variableSecret.value())),
        "{\"LOG_LEVEL\":\"debug\",\"EXPECTED_API_TOKEN_HASH\":\"%s\"}"
            .formatted(sha256(environmentSecret.value())),
        variableSecret,
        environmentSecret,
        "bookwright-variable-template-" + suffix,
        "variables.yml",
        "semaphore-bookwright-variable-group-ok",
        "Environment variables key can not be empty");
  }

  public VariableGroupRequest createRequest(long projectId) {
    return new VariableGroupRequest(
        0,
        name,
        projectId,
        json,
        env,
        List.of(variableSecret.createRequest(), environmentSecret.createRequest()));
  }

  public VariableGroupRequest renameRequest(long projectId, VariableGroup group) {
    VariableGroupSecret saved =
        group.secrets().stream()
            .filter(secret -> variableSecret.name().equals(secret.name()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Variable Group response omitted the variable-secret metadata"));
    return new VariableGroupRequest(
        group.id(),
        name,
        projectId,
        json,
        env,
        List.of(
            new VariableGroupSecretRequest(
                saved.id(), saved.type(), renamedVariable, "", "update")));
  }

  public VariableGroupRequest invalidEmptyEnvironmentNameRequest(long projectId) {
    return new VariableGroupRequest(
        0, name + "-invalid", projectId, "{}", "{\"\":\"value\"}", List.of());
  }

  public TemplateRequest templateRequest(
      long projectId, long repositoryId, long inventoryId, long variableGroupId) {
    return new TemplateRequest(
        templateName,
        projectId,
        inventoryId,
        repositoryId,
        variableGroupId,
        playbook,
        "ansible",
        "");
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is not available", error);
    }
  }

  public record Secret(String type, String name, String value) {

    public VariableGroupSecretRequest createRequest() {
      return new VariableGroupSecretRequest(0, type, name, value, "create");
    }

    @Override
    public String toString() {
      return "Secret[type=%s, name=%s, value=[REDACTED]]".formatted(type, name);
    }
  }
}
