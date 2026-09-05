package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.AnsibleTemplateParameters;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.util.TestData;
import java.util.List;

/** Typed data for static inventory group selection during Ansible execution. */
public record SemaphoreStaticInventoryFixtures(
    ProjectRequest project,
    AccessKey accessKey,
    Repository repository,
    StaticInventory iniInventory,
    Template iniTemplate,
    StaticInventory yamlInventory,
    Template yamlTemplate,
    String outputMarker) {

  public static SemaphoreStaticInventoryFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreStaticInventoryFixtures(
        new ProjectRequest("bookwright-static-inventory-" + suffix, false, 0),
        new AccessKey("bookwright-static-inventory-key-" + suffix, "none"),
        new Repository(
            "bookwright-static-inventory-repository-" + suffix, fixturesRepository(), fixturesDefaultBranch()),
        new StaticInventory(
            "bookwright-ini-inventory-" + suffix,
            "[bookwright_selected]\n"
                + "selected-host ansible_host=localhost ansible_connection=local\n\n"
                + "[bookwright_excluded]\n"
                + "excluded-host ansible_host=localhost ansible_connection=local",
            "static",
            "selected-host",
            "excluded-host"),
        new Template(
            "bookwright-ini-inventory-template-" + suffix,
            "smoke.yml",
            "ansible",
            "",
            "bookwright_selected"),
        new StaticInventory(
            "bookwright-yaml-inventory-" + suffix,
            "all:\n"
                + "  children:\n"
                + "    bookwright_yaml_selected:\n"
                + "      hosts:\n"
                + "        yaml-selected-host:\n"
                + "          ansible_host: localhost\n"
                + "          ansible_connection: local\n"
                + "    bookwright_yaml_excluded:\n"
                + "      hosts:\n"
                + "        yaml-excluded-host:\n"
                + "          ansible_host: localhost\n"
                + "          ansible_connection: local",
            "static-yaml",
            "yaml-selected-host",
            "yaml-excluded-host"),
        new Template(
            "bookwright-yaml-inventory-template-" + suffix,
            "smoke.yml",
            "ansible",
            "",
            "bookwright_yaml_selected"),
        "semaphore-bookwright-smoke-ok");
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

  public record StaticInventory(
      String name, String content, String type, String selectedHost, String excludedHost) {
    public InventoryRequest request(long projectId, long keyId) {
      return new InventoryRequest(name, projectId, content, keyId, type);
    }
  }

  public record Template(String name, String playbook, String app, String type, String limit) {
    public TemplateRequest request(long projectId, long repositoryId, long inventoryId) {
      return new TemplateRequest(
          name,
          projectId,
          inventoryId,
          repositoryId,
          0,
          playbook,
          app,
          type,
          null,
          false,
          List.of(),
          new AnsibleTemplateParameters(
              false,
              false,
              false,
              false,
              false,
              false,
              false,
              List.of(limit),
              List.of(),
              List.of()),
          null,
          false);
    }
  }
}
