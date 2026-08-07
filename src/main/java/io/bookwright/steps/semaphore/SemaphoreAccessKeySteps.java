package io.bookwright.steps.semaphore;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.AccessKey;
import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.semaphore.SemaphoreAccessKeysApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.UUID;

public class SemaphoreAccessKeySteps {

  private final SemaphoreAccessKeysApi api;
  private final TeardownStorage teardown;

  @Inject
  public SemaphoreAccessKeySteps(SemaphoreAccessKeysApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create no-auth access key in Semaphore project {projectId}")
  public AccessKey createNoneAccessKey(long projectId) {
    AccessKey key =
        Calls.body(
            api.createAccessKey(
                projectId,
                new AccessKeyRequest(
                    "bookwright-none-key-" + UUID.randomUUID(), "none", projectId)),
            201,
            "created access key");
    teardown.push(
        "Delete Semaphore access key " + key.id(),
        () -> Calls.expectStatus(api.deleteAccessKey(projectId, key.id()), 204));
    return key;
  }
}
