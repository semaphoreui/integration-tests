package io.bookwright.steps.ui.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Task;
import io.bookwright.ui.SemaphoreCorePage;
import io.qameta.allure.Step;

public class CoreSmokeSteps {

  private final SemaphoreCorePage page;

  @Inject
  public CoreSmokeSteps(SemaphoreCorePage page) {
    this.page = page;
  }

  @Step("Log in to Semaphore with password authentication")
  public void login() {
    page.openLogin();
    assertThat(page.submitPasswordLogin()).as("password login status").isEqualTo(204);
    page.waitForLoginScreenToClose();
    assertThat(page.currentUserStatus()).as("authenticated browser session status").isEqualTo(200);
  }

  @Step("Run Semaphore template {templateId} through the browser")
  public Task runTemplate(long projectId, long templateId) {
    return page.runTemplate(projectId, templateId);
  }

  @Step("Verify the browser rejects a project without a name")
  public void emptyProjectNameIsRejected() {
    page.openNewProject();
    assertThat(page.submitEmptyProjectName())
        .as("project create requests after client-side validation")
        .isZero();
    assertThat(page.projectNameValidation().textContent())
        .as("project name validation message")
        .isNotBlank();
  }
}
