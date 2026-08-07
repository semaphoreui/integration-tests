package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InventoryRequest(
    String name,
    @JsonProperty("project_id") long projectId,
    String inventory,
    @JsonProperty("ssh_key_id") long sshKeyId,
    String type) {}
