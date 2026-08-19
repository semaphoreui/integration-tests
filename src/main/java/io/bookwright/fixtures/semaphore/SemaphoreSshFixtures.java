package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.SshKeyRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.util.TestData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Typed Git-over-SSH and Ansible SSH target fixtures. */
public record SemaphoreSshFixtures(
    ProjectRequest project,
    SshAccessKey validKey,
    SshAccessKey invalidKey,
    Repository repository,
    Inventory inventory,
    Template template,
    String successfulTaskStatus,
    String failedTaskStatus,
    String outputMarker,
    String cloneFailureMarker) {

  public static SemaphoreSshFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreSshFixtures(
        new ProjectRequest("bookwright-ssh-" + suffix, false, 0),
        new SshAccessKey(
            "bookwright-ssh-key-" + suffix,
            "ssh",
            "fixture",
            "Bookwright-ssh-passphrase-42!",
            readPrivateKey()),
        new SshAccessKey(
            "bookwright-invalid-ssh-key-" + suffix,
            "ssh",
            "fixture",
            "bookwright-invalid-passphrase-" + suffix,
            "bookwright-invalid-private-key-" + suffix),
        new Repository(
            "bookwright-ssh-repository-" + suffix,
            "ssh://fixture@ssh-fixture:22/repositories/ansible",
            "main"),
        new Inventory(
            "bookwright-ssh-inventory-" + suffix,
            "[ssh_target]\n"
                + "ssh-fixture ansible_connection=ssh ansible_user=fixture ansible_port=22 "
                + "ansible_ssh_common_args='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null'",
            "static"),
        new Template("bookwright-ssh-template-" + suffix, "ssh.yml", "ansible", ""),
        "success",
        "error",
        "semaphore-bookwright-ssh-target-ok",
        "Failed updating repository");
  }

  private static String readPrivateKey() {
    Path path = Path.of("build", "test-fixtures", "ssh", "id_ed25519");
    try {
      return Files.readString(path);
    } catch (IOException error) {
      throw new IllegalStateException(
          "Could not read generated SSH fixture key from %s; run ".formatted(path.toAbsolutePath())
              + "test-environment/profile up feature-ssh-local first",
          error);
    }
  }

  public record SshAccessKey(
      String name, String type, String login, String passphrase, String privateKey) {

    public AccessKeyRequest request(long projectId) {
      return new AccessKeyRequest(
          name, type, projectId, null, new SshKeyRequest(login, passphrase, privateKey));
    }

    @Override
    public String toString() {
      return "SshAccessKey[name=%s, type=%s, login=%s, passphrase=[REDACTED], privateKey=[REDACTED]]"
          .formatted(name, type, login);
    }
  }

  public record Repository(String name, String gitUrl, String gitBranch) {
    public RepositoryRequest request(long projectId, long keyId) {
      return new RepositoryRequest(name, projectId, gitUrl, gitBranch, keyId);
    }
  }

  public record Inventory(String name, String content, String type) {
    public InventoryRequest request(long projectId, long keyId) {
      return new InventoryRequest(name, projectId, content, keyId, type);
    }
  }

  public record Template(String name, String playbook, String app, String type) {
    public TemplateRequest request(long projectId, long repositoryId, long inventoryId) {
      return new TemplateRequest(
          name, projectId, inventoryId, repositoryId, 0, playbook, app, type);
    }
  }
}
