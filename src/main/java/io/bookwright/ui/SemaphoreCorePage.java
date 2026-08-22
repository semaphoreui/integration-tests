package io.bookwright.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.bookwright.api.model.semaphore.Task;
import io.bookwright.config.MainConfig;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Stable browser controls used by the small password-authenticated Semaphore smoke suite. */
public class SemaphoreCorePage {

  private static final ObjectMapper JSON =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final Page page;
  private final MainConfig config;

  @Inject
  public SemaphoreCorePage(Page page, MainConfig config) {
    this.page = page;
    this.config = config;
  }

  public void openLogin() {
    page.navigate(config.uiBaseUrl() + "/auth/login?return=/");
  }

  public int submitPasswordLogin() {
    page.getByTestId("auth-username").fill(config.uiUser());
    page.getByTestId("auth-password").fill(config.uiPassword());
    return page.waitForResponse(
            response ->
                "POST".equals(response.request().method())
                    && response.url().endsWith("/api/auth/login"),
            () -> page.getByTestId("auth-signin").click())
        .status();
  }

  public void waitForLoginScreenToClose() {
    page.getByTestId("auth-signin")
        .waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15_000));
  }

  public int currentUserStatus() {
    APIResponse response = page.context().request().get(config.uiBaseUrl() + "/api/user");
    try {
      return response.status();
    } finally {
      response.dispose();
    }
  }

  public Task runTemplate(long projectId, long templateId) {
    page.navigate(
        config.uiBaseUrl() + "/project/%d/templates/%d/tasks".formatted(projectId, templateId));
    page.getByTestId("template-run").click();
    Locator dialog = page.getByTestId("newTaskDialog");
    dialog.waitFor();
    Response response =
        page.waitForResponse(
            candidate ->
                "POST".equals(candidate.request().method())
                    && candidate.url().endsWith("/api/project/%d/tasks".formatted(projectId)),
            () -> dialog.getByTestId("editDialog-save").click());
    String body = response.text();
    if (response.status() != 201) {
      throw new IllegalStateException(
          "Semaphore UI task launch returned HTTP %d: %s".formatted(response.status(), body));
    }
    try {
      return JSON.readValue(body, Task.class);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Semaphore UI returned an invalid task payload", error);
    }
  }

  public void openNewProject() {
    page.navigate(config.uiBaseUrl() + "/project/new");
    page.getByTestId("newProject-name").waitFor();
  }

  public int submitEmptyProjectName() {
    AtomicInteger createRequests = new AtomicInteger();
    Consumer<Request> requestListener =
        request -> {
          if ("POST".equals(request.method()) && request.url().endsWith("/api/projects")) {
            createRequests.incrementAndGet();
          }
        };
    page.onRequest(requestListener);
    try {
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create").setExact(true))
          .click();
      projectNameValidation().waitFor();
      return createRequests.get();
    } finally {
      page.offRequest(requestListener);
    }
  }

  public Locator projectNameValidation() {
    return page.locator(".v-input:has([data-testid='newProject-name']) .v-messages__message");
  }
}
