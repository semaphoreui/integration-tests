package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Task(
    long id,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("template_id") long templateId,
    String status,
    @JsonProperty("commit_hash") String commitHash,
    @JsonProperty("schedule_id") Long scheduleId,
    String message,
    String environment,
    String arguments,
    AnsibleTaskParameters params,
    @JsonProperty("build_task_id") Long buildTaskId,
    String version,
    @JsonProperty("build_task") Task buildTask,
    @JsonProperty("integration_id") Long integrationId,
    @JsonProperty("used_runner_id") Long usedRunnerId) {}
