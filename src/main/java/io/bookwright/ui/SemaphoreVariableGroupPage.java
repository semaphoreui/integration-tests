package io.bookwright.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.bookwright.config.MainConfig;

/** Browser controls for editing an existing Semaphore Variable Group. */
public class SemaphoreVariableGroupPage {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final Page page;
  private final MainConfig config;

  @Inject
  public SemaphoreVariableGroupPage(Page page, MainConfig config) {
    this.page = page;
    this.config = config;
  }

  public void open(long projectId, String groupName) {
    page.navigate(config.uiBaseUrl() + "/project/%d/environment".formatted(projectId));
    page.getByText(groupName, new Page.GetByTextOptions().setExact(true)).click();
    dialog().waitFor();
    dialog()
        .getByRole(AriaRole.TAB, new Locator.GetByRoleOptions().setName("Secrets").setExact(true))
        .click();
  }

  public VariableGroupSave renameSecret(
      long projectId, long groupId, String currentName, String newName) {
    secretNameInput(currentName).fill(newName);
    Response response =
        page.waitForResponse(
            candidate ->
                "PUT".equals(candidate.request().method())
                    && candidate
                        .url()
                        .endsWith("/api/project/%d/environment/%d".formatted(projectId, groupId)),
            () -> dialog().getByTestId("editDialog-save").click());
    dialog()
        .waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15_000));
    return saveResult(response, newName);
  }

  public Locator secretNameInput(String name) {
    dialog().locator("input").first().waitFor();
    return dialog().locator("input").all().stream()
        .filter(input -> name.equals(input.inputValue()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Variable Group dialog does not contain a secret named " + name));
  }

  public void close() {
    dialog().getByTestId("editDialog-close").click();
  }

  private Locator dialog() {
    return page.getByTestId("varGroupDialog");
  }

  private VariableGroupSave saveResult(Response response, String expectedName) {
    String requestBody = response.request().postData();
    if (requestBody == null || requestBody.isBlank()) {
      throw new IllegalStateException("Variable Group UI update sent an empty request body");
    }
    try {
      JsonNode secrets = JSON.readTree(requestBody).path("secrets");
      JsonNode renamedSecret =
          secrets
              .valueStream()
              .filter(candidate -> expectedName.equals(candidate.path("name").asText()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Variable Group UI update omitted the renamed secret " + expectedName));
      return new VariableGroupSave(
          response.status(),
          renamedSecret.path("id").asLong(),
          renamedSecret.path("type").asText(),
          renamedSecret.path("name").asText(),
          renamedSecret.path("operation").asText(),
          secrets.valueStream().allMatch(secret -> secret.path("secret").asText().isEmpty()));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Variable Group UI sent an invalid JSON request", error);
    }
  }

  public record VariableGroupSave(
      int status,
      long secretId,
      String secretType,
      String secretName,
      String operation,
      boolean secretValuesBlank) {}
}
