package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AnsibleTaskParameters;
import io.bookwright.api.model.semaphore.AnsibleTemplateParameters;
import io.bookwright.api.model.semaphore.SurveyVariable;
import io.bookwright.api.model.semaphore.SurveyVariableValue;
import io.bookwright.api.model.semaphore.TaskRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.util.TestData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Survey metadata and launch-time override data for an executable Ansible scenario. */
public record SemaphoreSurveyFixtures(
    String templateName,
    String playbook,
    List<SurveyVariable> surveyVariables,
    AnsibleTemplateParameters templateParameters,
    String templateArguments,
    String taskEnvironment,
    Secret taskSecret,
    String taskArguments,
    AnsibleTaskParameters taskParameters,
    String taskMessage,
    String outputMarker,
    String invalidTarget,
    String expectedValidationError) {

  public static SemaphoreSurveyFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    Secret secret = new Secret("survey_api_token", "bookwright-survey-token-" + suffix);
    List<SurveyVariableValue> environments =
        List.of(
            new SurveyVariableValue("Development", "dev"),
            new SurveyVariableValue("Staging", "stg"),
            new SurveyVariableValue("Production", "prod"));
    List<SurveyVariable> surveyVariables =
        List.of(
            new SurveyVariable(
                "deployment_env",
                "Environment",
                true,
                "enum",
                "",
                "Deployment environment",
                environments,
                "dev"),
            new SurveyVariable(
                "replicas", "Replica count", true, "int", "", "Requested replicas", List.of(), "1"),
            new SurveyVariable(
                "feature_name", "Feature", false, "", "", "Optional feature", List.of(), null),
            new SurveyVariable(
                "runtime_label",
                "Runtime label",
                true,
                "",
                "env",
                "Process environment value",
                List.of(),
                "template-default"),
            new SurveyVariable(
                secret.name(),
                "API token",
                true,
                "secret",
                "",
                "Masked survey value",
                List.of(),
                null));
    return new SemaphoreSurveyFixtures(
        "bookwright-survey-template-" + suffix,
        "survey-overrides.yml",
        surveyVariables,
        new AnsibleTemplateParameters(
            true, false, true, true, true, true, false, List.of(), List.of(), List.of()),
        "[\"--extra-vars\",\"template_arg=base\"]",
        "{\"deployment_env\":\"stg\",\"replicas\":\"2\",\"feature_name\":\"login-redesign\",\"runtime_label\":\"launch-env\",\"expected_survey_secret_hash\":\"%s\"}"
            .formatted(sha256(secret.value())),
        secret,
        "[\"--extra-vars\",\"task_override=launch\"]",
        new AnsibleTaskParameters(
            false,
            4,
            false,
            true,
            List.of("local"),
            List.of("survey"),
            List.of("suppressed"),
            true),
        "bookwright survey launch " + suffix,
        "semaphore-bookwright-survey-overrides-ok",
        "process",
        "invalid survey variable target: process");
  }

  public TemplateRequest templateRequest(long projectId, long repositoryId, long inventoryId) {
    return new TemplateRequest(
        templateName,
        projectId,
        inventoryId,
        repositoryId,
        0,
        playbook,
        "ansible",
        "",
        templateArguments,
        true,
        surveyVariables,
        templateParameters);
  }

  public TemplateRequest invalidTemplateRequest(
      long projectId, long repositoryId, long inventoryId) {
    SurveyVariable valid = surveyVariables.getFirst();
    SurveyVariable invalid =
        new SurveyVariable(
            valid.name(),
            valid.title(),
            valid.required(),
            valid.type(),
            invalidTarget,
            valid.description(),
            valid.values(),
            valid.defaultValue());
    return new TemplateRequest(
        templateName + "-invalid",
        projectId,
        inventoryId,
        repositoryId,
        0,
        playbook,
        "ansible",
        "",
        templateArguments,
        true,
        List.of(invalid),
        templateParameters);
  }

  public TaskRequest taskRequest(long templateId) {
    return new TaskRequest(
        templateId,
        taskEnvironment,
        "{\"%s\":\"%s\"}".formatted(taskSecret.name(), taskSecret.value()),
        taskArguments,
        taskParameters,
        taskMessage);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is not available", error);
    }
  }

  @Override
  public String toString() {
    return "SemaphoreSurveyFixtures[templateName=%s, playbook=%s, surveyVariables=%s, taskSecret=[REDACTED], outputMarker=%s]"
        .formatted(templateName, playbook, surveyVariables, outputMarker);
  }

  public record Secret(String name, String value) {

    @Override
    public String toString() {
      return "Secret[name=%s, value=[REDACTED]]".formatted(name);
    }
  }
}
