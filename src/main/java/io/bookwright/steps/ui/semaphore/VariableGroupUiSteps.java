package io.bookwright.steps.ui.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import io.bookwright.api.model.semaphore.VariableGroup;
import io.bookwright.api.model.semaphore.VariableGroupSecret;
import io.bookwright.ui.SemaphoreVariableGroupPage;
import io.qameta.allure.Step;

public class VariableGroupUiSteps {

  private final SemaphoreVariableGroupPage page;

  @Inject
  public VariableGroupUiSteps(SemaphoreVariableGroupPage page) {
    this.page = page;
  }

  @Step("Rename Variable Group secret {currentName} to {newName} through the browser")
  public void renameSecret(
      long projectId, VariableGroup group, String currentName, String newName) {
    VariableGroupSecret original =
        group.secrets().stream()
            .filter(secret -> currentName.equals(secret.name()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Variable Group %d does not contain secret %s"
                            .formatted(group.id(), currentName)));

    page.open(projectId, group.name());
    var save = page.renameSecret(projectId, group.id(), currentName, newName);

    assertThat(save.status()).as("Variable Group UI update status").isEqualTo(204);
    assertThat(save.secretId()).as("updated secret id").isEqualTo(original.id());
    assertThat(save.secretType()).as("updated secret type").isEqualTo(original.type());
    assertThat(save.secretName()).as("updated secret name").isEqualTo(newName);
    assertThat(save.operation()).as("updated secret operation").isEqualTo("update");
    assertThat(save.secretValuesBlank())
        .as("UI must preserve stored values without resubmitting plaintext")
        .isTrue();

    page.open(projectId, group.name());
    PlaywrightAssertions.assertThat(page.secretNameInput(newName)).hasValue(newName);
    page.close();
  }
}
