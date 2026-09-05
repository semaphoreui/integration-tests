package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.TaskRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.api.model.semaphore.TerraformTaskParameters;
import io.bookwright.api.model.semaphore.TerraformTemplateParameters;
import io.bookwright.api.model.semaphore.VariableGroupRequest;
import io.bookwright.api.model.semaphore.VariableGroupSecretRequest;
import io.bookwright.util.TestData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import io.bookwright.config.MainConfig;

/** Typed data for Terraform and OpenTofu workspace plan execution. */
public record SemaphoreTerraformFixtures(
    ProjectRequest project,
    AccessKey accessKey,
    Repository repository,
    TerraformVariableGroup variableGroup,
    Tool terraform,
    Tool tofu,
    String workspaceOutputName) {

  public static SemaphoreTerraformFixtures from(MainConfig config, TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreTerraformFixtures(
        new ProjectRequest("bookwright-terraform-" + suffix, false, 0),
        new AccessKey("bookwright-terraform-key-" + suffix, "none"),
        new Repository(
            "bookwright-terraform-repository-" + suffix, config.fixturesRepository(), config.fixturesDefaultBranch()),
        new TerraformVariableGroup(
            "bookwright-terraform-variables-" + suffix,
            "TF_VAR_bookwright_secret",
            "bookwright-terraform-secret-" + suffix,
            "TF_VAR_bookwright_expected_hash",
            "semaphore_bookwright_tf_var_secret_verified"),
        new Tool(
            new WorkspaceInventory(
                "bookwright-terraform-workspace-" + suffix,
                "bookwright-tf-" + suffix,
                "test-environment/fixtures/ansible/terraform-workspace"),
            new ToolTemplate(
                "bookwright-terraform-template-" + suffix, "test-environment/fixtures/ansible/terraform-workspace", "terraform")),
        new Tool(
            new WorkspaceInventory(
                "bookwright-tofu-workspace-" + suffix,
                "bookwright-tofu-" + suffix,
                "tofu-workspace"),
            new ToolTemplate("bookwright-tofu-template-" + suffix, "test-environment/fixtures/ansible/terraform-workspace", "tofu")),
        "semaphore_bookwright_workspace");
  }

  public record AccessKey(String name, String type) {
    public AccessKeyRequest request(long projectId) {
      return new AccessKeyRequest(name, type, projectId);
    }
  }

  public record Repository(String name, String gitUrl, String gitBranch) {
    public RepositoryRequest request(long projectId, long keyId) {
      return new RepositoryRequest(name, projectId, gitUrl, gitBranch, keyId);
    }
  }

  public record WorkspaceInventory(String name, String workspace, String type) {
    public InventoryRequest request(long projectId) {
      return new InventoryRequest(name, projectId, workspace, null, null, null, type);
    }
  }

  public record ToolTemplate(String name, String directory, String app) {
    public TemplateRequest request(
        long projectId, long repositoryId, long inventoryId, long variableGroupId) {
      return new TemplateRequest(
          name,
          projectId,
          inventoryId,
          repositoryId,
          variableGroupId,
          directory,
          app,
          "",
          null,
          false,
          List.of(),
          new TerraformTemplateParameters(false, false, false, false, null),
          null,
          false);
    }

    public TaskRequest planRequest(long templateId) {
      return new TaskRequest(
          templateId,
          null,
          null,
          null,
          new TerraformTaskParameters(true, false, false, false, false),
          null);
    }
  }

  public record TerraformVariableGroup(
      String name,
      String secretName,
      String secretValue,
      String expectedHashName,
      String outputMarker) {

    public VariableGroupRequest request(long projectId) {
      return new VariableGroupRequest(
          0,
          name,
          projectId,
          "{}",
          "{\"%s\":\"%s\"}".formatted(expectedHashName, sha256(secretValue)),
          List.of(new VariableGroupSecretRequest(0, "env", secretName, secretValue, "create")));
    }

    @Override
    public String toString() {
      return "TerraformVariableGroup[name=%s, secretName=%s, secretValue=[REDACTED], expectedHashName=%s, outputMarker=%s]"
          .formatted(name, secretName, expectedHashName, outputMarker);
    }
  }

  public record Tool(WorkspaceInventory inventory, ToolTemplate template) {}

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is not available", error);
    }
  }
}
