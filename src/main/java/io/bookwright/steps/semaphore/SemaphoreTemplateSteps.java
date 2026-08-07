package io.bookwright.steps.semaphore;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.api.semaphore.SemaphoreTemplatesApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.UUID;

public class SemaphoreTemplateSteps {

  private final SemaphoreTemplatesApi api;
  private final TeardownStorage teardown;

  @Inject
  public SemaphoreTemplateSteps(SemaphoreTemplatesApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create Ansible task template in Semaphore project {projectId}")
  public Template createAnsibleTemplate(long projectId, long repositoryId, long inventoryId) {
    Template template =
        Calls.body(
            api.createTemplate(
                projectId,
                new TemplateRequest(
                    "bookwright-build-template-" + UUID.randomUUID(),
                    projectId,
                    inventoryId,
                    repositoryId,
                    0,
                    "smoke.yml",
                    "ansible",
                    "")),
            201,
            "created task template");
    teardown.push(
        "Delete Semaphore task template " + template.id(),
        () -> Calls.expectStatus(api.deleteTemplate(projectId, template.id()), 204));
    return template;
  }
}
