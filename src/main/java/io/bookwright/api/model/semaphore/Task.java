package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Task(
    long id,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("template_id") long templateId,
    String status,
    @JsonProperty("commit_hash") String commitHash) {}
