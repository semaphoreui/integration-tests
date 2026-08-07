package io.bookwright.steps.semaphore.templates;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.api.semaphore.templates.SemaphoreTemplatesApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;

public class TemplateSteps {

  private final SemaphoreTemplatesApi api;
  private final TeardownStorage teardown;

  @Inject
  public TemplateSteps(SemaphoreTemplatesApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create Ansible task template in Semaphore project {projectId}")
  public Template create(long projectId, TemplateRequest request) {
    Template template =
        Calls.body(api.createTemplate(projectId, request), 201, "created task template");
    teardown.push(
        "Delete Semaphore task template " + template.id(),
        () -> Calls.expectStatus(api.deleteTemplate(projectId, template.id()), 204));
    return template;
  }
}
