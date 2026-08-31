package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Inventory(
    long id,
    String name,
    @JsonProperty("project_id") long projectId,
    String inventory,
    @JsonProperty("ssh_key_id") Long sshKeyId,
    String type,
    @JsonProperty("repository_id") Long repositoryId) {}
