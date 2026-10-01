package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.Template;
import io.bookwright.api.model.semaphore.TemplateUpdateRequest;
import io.bookwright.util.TestData;
import java.util.List;

/** Updated settings for the executable template supplied by the precondition. */
public record SemaphoreTemplateCrudFixtures(
    String updatedName, String playbook, String arguments, String checkModeMarker) {

  public static SemaphoreTemplateCrudFixtures from(TestData data) {
    return new SemaphoreTemplateCrudFixtures(
        "bookwright-crud-template-updated-" + Long.toUnsignedString(data.testSeed(), 36),
        "ansible/template-update.yml",
        "[\"--check\"]",
        "semaphore-bookwright-template-check-mode-ok");
  }

  public TemplateUpdateRequest updateRequest(Template template) {
    return new TemplateUpdateRequest(
        template.id(),
        updatedName,
        template.projectId(),
        template.inventoryId(),
        template.repositoryId(),
        0,
        playbook,
        template.app(),
        template.type(),
        arguments,
        true,
        List.of(),
        null,
        null,
        false,
        null,
        null,
        false,
        false);
  }
}
