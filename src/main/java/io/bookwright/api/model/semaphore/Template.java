package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

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
    @JsonProperty("allow_override_args_in_task") boolean allowOverrideArgsInTask,
    @JsonProperty("survey_vars") List<SurveyVariable> surveyVariables,
    @JsonProperty("task_params") AnsibleTemplateParameters taskParameters) {}
