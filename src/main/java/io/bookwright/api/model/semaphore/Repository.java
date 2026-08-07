package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Repository(
    long id,
    String name,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("git_url") String gitUrl,
    @JsonProperty("git_branch") String gitBranch,
    @JsonProperty("ssh_key_id") long sshKeyId) {}
