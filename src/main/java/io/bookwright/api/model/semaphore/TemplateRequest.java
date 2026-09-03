package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

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
    @JsonProperty("allow_override_args_in_task") boolean allowOverrideArgsInTask,
    @JsonProperty("survey_vars") List<SurveyVariable> surveyVariables,
    @JsonProperty("task_params") TemplateParameters taskParameters,
    @JsonProperty("runner_tag") String runnerTag,
    @JsonProperty("allow_parallel_tasks") boolean allowParallelTasks,
    @JsonProperty("start_version") String startVersion,
    @JsonProperty("build_template_id") Long buildTemplateId,
    boolean autorun) {

  public TemplateRequest(
      String name,
      long projectId,
      long inventoryId,
      long repositoryId,
      long environmentId,
      String playbook,
      String app,
      String type,
      String arguments,
      boolean allowOverrideArgsInTask,
      List<SurveyVariable> surveyVariables,
      TemplateParameters taskParameters,
      String runnerTag,
      boolean allowParallelTasks) {
    this(
        name,
        projectId,
        inventoryId,
        repositoryId,
        environmentId,
        playbook,
        app,
        type,
        arguments,
        allowOverrideArgsInTask,
        surveyVariables,
        taskParameters,
        runnerTag,
        allowParallelTasks,
        null,
        null,
        false);
  }

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
        name,
        projectId,
        inventoryId,
        repositoryId,
        environmentId,
        playbook,
        app,
        type,
        null,
        false,
        List.of(),
        null,
        null,
        false,
        null,
        null,
        false);
  }
}
