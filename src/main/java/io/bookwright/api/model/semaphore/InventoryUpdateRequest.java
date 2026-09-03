package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InventoryUpdateRequest(
    long id,
    String name,
    @JsonProperty("project_id") long projectId,
    String inventory,
    @JsonProperty("ssh_key_id") long sshKeyId,
    @JsonProperty("become_key_id") Long becomeKeyId,
    @JsonProperty("repository_id") Long repositoryId,
    String type) {}
