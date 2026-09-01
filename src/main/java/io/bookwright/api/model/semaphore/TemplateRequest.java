package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TemplateRequest(
    String name,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("inventory_id") long inventoryId,
    @JsonProperty("repository_id") long repositoryId,
    @JsonProperty("environment_id") long environmentId,
    String playbook,
    String app,
    String type,
    String arguments,
    @JsonProperty("working_directory") String workingDirectory) {

  public TemplateRequest(
      String name,
      long projectId,
      long inventoryId,
      long repositoryId,
      long environmentId,
      String playbook,
      String app,
      String type) {
    this(
        name, projectId, inventoryId, repositoryId, environmentId, playbook, app, type, null, null);
  }
}
