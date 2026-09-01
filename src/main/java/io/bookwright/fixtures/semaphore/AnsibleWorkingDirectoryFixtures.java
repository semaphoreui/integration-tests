package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.config.MainConfig;
import io.bookwright.util.TestData;

/** Typed resources and scenarios for Ansible working-directory integration tests. */
public record AnsibleWorkingDirectoryFixtures(
    ProjectRequest project,
    String accessKeyName,
    String repositoryName,
    String repositoryUrl,
    String repositoryBranch,
    String inventoryName,
    String inventoryPath,
    String workingDirectory,
    Scenario roleDiscovery,
    Scenario extraVars,
    Scenario privateKey) {

  private static final String EXTRA_VARS_ARGUMENTS =
      """
      ["--extra-vars", "@vars.yml"]
      """
          .strip();

  private static final String PRIVATE_KEY_ARGUMENTS =
      """
      ["--private-key", "key.pem"]
      """
          .strip();

  public static AnsibleWorkingDirectoryFixtures from(MainConfig config, TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    String root = "test-environment/fixtures/ansible/working-directory-fixture";
    return new AnsibleWorkingDirectoryFixtures(
        new ProjectRequest("bookwright-working-directory-" + suffix, false, 0),
        "bookwright-working-directory-key-" + suffix,
        "bookwright-working-directory-repository-" + suffix,
        config.semaphoreRepositoryUrl(),
        config.semaphoreRepositoryBranch(),
        "bookwright-working-directory-inventory-" + suffix,
        root + "/inventory.ini",
        root + "/working-dir",
        new Scenario(
            "bookwright-working-directory-role-" + suffix,
            root + "/playbooks/site.yml",
            null,
            "SEMAPHORE_WORKING_DIRECTORY_FIXTURE_OK"),
        new Scenario(
            "bookwright-working-directory-extra-vars-" + suffix,
            root + "/playbooks/extra_vars_path_resolution.yml",
            EXTRA_VARS_ARGUMENTS,
            "SEMAPHORE_WD_EXTRA_VARS_OK"),
        new Scenario(
            "bookwright-working-directory-private-key-" + suffix,
            root + "/playbooks/private_key_path_resolution.yml",
            PRIVATE_KEY_ARGUMENTS,
            "SEMAPHORE_WD_PRIVATE_KEY_PATH_OK"));
  }

  public AccessKeyRequest accessKey(long projectId) {
    return new AccessKeyRequest(accessKeyName, "none", projectId);
  }

  public RepositoryRequest repository(long projectId, long keyId) {
    return new RepositoryRequest(repositoryName, projectId, repositoryUrl, repositoryBranch, keyId);
  }

  public InventoryRequest inventory(long projectId, long keyId) {
    return new InventoryRequest(inventoryName, projectId, inventoryPath, keyId, "file");
  }

  public record Scenario(String name, String playbook, String arguments, String outputMarker) {

    public TemplateRequest template(
        long projectId, long repositoryId, long inventoryId, String workingDirectory) {
      return new TemplateRequest(
          name,
          projectId,
          inventoryId,
          repositoryId,
          0,
          playbook,
          "ansible",
          "",
          arguments,
          workingDirectory);
    }
  }
}
