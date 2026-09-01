package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Template(
    long id,
    String name,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("inventory_id") long inventoryId,
    @JsonProperty("repository_id") long repositoryId,
    String playbook,
    String app,
    String type,
    String arguments,
    @JsonProperty("working_directory") String workingDirectory) {}
