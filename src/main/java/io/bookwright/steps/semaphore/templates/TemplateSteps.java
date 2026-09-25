package io.bookwright.steps.semaphore.templates;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.TaskStopRequest;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.api.model.semaphore.TemplateUpdateRequest;
import io.bookwright.api.semaphore.templates.SemaphoreTemplatesApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.io.IOException;
import java.util.List;

public class TemplateSteps {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final SemaphoreTemplatesApi api;
  private final TeardownStorage teardown;

  @Inject
  public TemplateSteps(SemaphoreTemplatesApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Delete Semaphore template {templateId} in project {projectId}")
  public void delete(long projectId, long templateId) {
    Calls.expectStatus(api.deleteTemplate(projectId, templateId), 204);
  }

  @Step("Verify Semaphore template {templateId} is absent")
  public void verifyAbsent(long projectId, long templateId) {
    Calls.expectStatus(api.getTemplate(projectId, templateId), 404);
    if (Calls.body(api.getTemplates(projectId), 200, "template collection").stream()
        .anyMatch(item -> item.id() == templateId)) {
      throw new IllegalStateException(
          "Deleted Semaphore template %d is still listed in project %d"
              .formatted(templateId, projectId));
    }
  }

  @Step("Create task template in Semaphore project {projectId}")
  public Template create(long projectId, TemplateRequest request) {
    Template template =
        Calls.body(api.createTemplate(projectId, request), 201, "created task template");
    teardown.push(
        "Delete Semaphore task template " + template.id(),
        () ->
            Calls.expectStatus(
                Calls.response(api.deleteTemplate(projectId, template.id())), 204, 404));
    return template;
  }

  @Step("Verify Semaphore rejects invalid template {request.name}")
  public void verifyRejected(
      long projectId, TemplateRequest request, String expectedValidationError) {
    var response = Calls.response(api.createTemplate(projectId, request));
    Calls.expectStatus(response, 400);
    try (var body = response.errorBody()) {
      String diagnostic = body == null ? "" : body.string();
      String validationMessage = validationMessage(diagnostic);
      if (!validationMessage.contains(expectedValidationError)) {
        throw new IllegalStateException(
            "Template validation response did not contain '%s'. Body: %s"
                .formatted(expectedValidationError, diagnostic));
      }
    } catch (IOException error) {
      throw new IllegalStateException(
          "Could not read Semaphore template validation response", error);
    }
  }

  private String validationMessage(String responseBody) {
    try {
      var document = JSON.readTree(responseBody);
      return document.hasNonNull("error") ? document.get("error").asText() : responseBody;
    } catch (JsonProcessingException ignored) {
      return responseBody;
    }
  }

  @Step("Get Semaphore template {templateId} in project {projectId}")
  public Template get(long projectId, long templateId) {
    return Calls.body(api.getTemplate(projectId, templateId), 200, "task template");
  }

  @Step("Update Semaphore template {templateId} in project {projectId}")
  public Template update(long projectId, long templateId, TemplateUpdateRequest request) {
    Calls.expectStatus(api.updateTemplate(projectId, templateId, request), 204);
    return get(projectId, templateId);
  }

  @Step("Stop all active tasks for Semaphore template {templateId}")
  public void stopAllTasks(long projectId, long templateId, boolean force) {
    Calls.expectStatus(api.stopAllTasks(projectId, templateId, new TaskStopRequest(force)), 204);
  }

  @Step("Find required template {name} in Semaphore project {projectId}")
  public Template requireByName(long projectId, String name) {
    List<Template> templates = Calls.body(api.getTemplates(projectId), 200, "templates");
    return templates.stream()
        .filter(template -> name.equals(template.name()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required template '%s' was not found in project %d. Available templates: %s"
                        .formatted(
                            name, projectId, templates.stream().map(Template::name).toList())));
  }
}
