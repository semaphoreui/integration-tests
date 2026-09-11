package io.bookwright.fixtures.semaphore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.bookwright.api.model.semaphore.Inventory;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.Repository;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.config.ExternalManagedConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Persistent, name-addressed fixture contract for a user-managed Semaphore stand. */
public record SemaphoreExternalManagedFixtures(
    String projectName,
    String repositoryName,
    String inventoryName,
    String fixtureRepository,
    String fixtureBranch,
    TaskTemplate templateA,
    TaskTemplate templateB) {

  private static final String BACKUP_RESOURCE = "/fixtures/external-managed-project.json";

  public static SemaphoreExternalManagedFixtures from(ExternalManagedConfig config) {
    if (!config.mutationsAllowed()) {
      throw new IllegalStateException(
          "Managed external checks are disabled. Set EXTERNAL_MUTATIONS_ALLOWED=true explicitly.");
    }
    if (config.fixtureRepository() == null || config.fixtureRepository().isBlank()) {
      throw new IllegalStateException("EXTERNAL_MANAGED_FIXTURE_REPOSITORY must not be blank");
    }
    if (config.fixtureBranch() == null || config.fixtureBranch().isBlank()) {
      throw new IllegalStateException("EXTERNAL_MANAGED_FIXTURE_BRANCH must not be blank");
    }

    return new SemaphoreExternalManagedFixtures(
        "bookwright-external-managed",
        "bookwright-external-managed-repository",
        "bookwright-external-managed-inventory",
        config.fixtureRepository(),
        config.fixtureBranch(),
        new TaskTemplate(
            "bookwright-external-managed-a",
            "test-environment/fixtures/ansible/external-managed-a.yml",
            "semaphore-bookwright-external-managed-a-ok"),
        new TaskTemplate(
            "bookwright-external-managed-b",
            "test-environment/fixtures/ansible/external-managed-b.yml",
            "semaphore-bookwright-external-managed-b-ok"));
  }

  public JsonNode projectBackup() {
    try (var stream = SemaphoreExternalManagedFixtures.class.getResourceAsStream(BACKUP_RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException(
            "Managed external project backup is missing: " + BACKUP_RESOURCE);
      }
      ObjectNode backup = (ObjectNode) new ObjectMapper().readTree(stream);
      ObjectNode repository = (ObjectNode) ((ArrayNode) backup.get("repositories")).get(0);
      repository.put("git_url", fixtureRepository);
      repository.put("git_branch", fixtureBranch);
      return backup;
    } catch (IOException error) {
      throw new IllegalStateException(
          "Could not read managed external project backup " + BACKUP_RESOURCE, error);
    }
  }

  public void validate(
      Project project,
      Repository repository,
      Inventory inventory,
      Template actualTemplateA,
      Template actualTemplateB) {
    var errors = new ArrayList<String>();
    if (project.maxParallelTasks() != 0 && project.maxParallelTasks() < 2) {
      errors.add("project max_parallel_tasks must be 0 or at least 2");
    }
    if (repository.projectId() != project.id()) {
      errors.add("repository belongs to project %d".formatted(repository.projectId()));
    }
    if (!fixtureRepository.equals(repository.gitUrl())) {
      errors.add(
          "repository URL is '%s', expected '%s'"
              .formatted(repository.gitUrl(), fixtureRepository));
    }
    if (!fixtureBranch.equals(repository.gitBranch())) {
      errors.add(
          "repository branch is '%s', expected '%s'"
              .formatted(repository.gitBranch(), fixtureBranch));
    }
    if (inventory.projectId() != project.id()) {
      errors.add("inventory belongs to project %d".formatted(inventory.projectId()));
    }
    if (!"static".equals(inventory.type())) {
      errors.add("inventory type is '%s', expected 'static'".formatted(inventory.type()));
    }
    validateTemplate(errors, project, repository, inventory, actualTemplateA, templateA);
    validateTemplate(errors, project, repository, inventory, actualTemplateB, templateB);
    if (actualTemplateA.id() == actualTemplateB.id()) {
      errors.add("the two configured template names resolved to the same ID");
    }
    if (!errors.isEmpty()) {
      throw new IllegalStateException(
          "Invalid managed external fixture '%s':\n - %s"
              .formatted(project.name(), String.join("\n - ", errors)));
    }
  }

  private void validateTemplate(
      List<String> errors,
      Project project,
      Repository repository,
      Inventory inventory,
      Template actual,
      TaskTemplate expected) {
    if (actual.projectId() != project.id()) {
      errors.add(
          "template '%s' belongs to project %d".formatted(actual.name(), actual.projectId()));
    }
    if (actual.repositoryId() != repository.id()) {
      errors.add(
          "template '%s' uses repository %d, expected %d"
              .formatted(actual.name(), actual.repositoryId(), repository.id()));
    }
    if (actual.inventoryId() != inventory.id()) {
      errors.add(
          "template '%s' uses inventory %d, expected %d"
              .formatted(actual.name(), actual.inventoryId(), inventory.id()));
    }
    if (!expected.playbook().equals(actual.playbook())) {
      errors.add(
          "template '%s' uses playbook '%s', expected '%s'"
              .formatted(actual.name(), actual.playbook(), expected.playbook()));
    }
    if (!"ansible".equals(actual.app())) {
      errors.add(
          "template '%s' uses application '%s', expected 'ansible'"
              .formatted(actual.name(), actual.app()));
    }
  }

  public record TaskTemplate(String name, String playbook, String marker) {}
}
