package io.bookwright.steps.semaphore.accesskeys;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.AccessKey;
import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.semaphore.accesskeys.SemaphoreAccessKeysApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;

public class AccessKeySteps {

  private final SemaphoreAccessKeysApi api;
  private final TeardownStorage teardown;

  @Inject
  public AccessKeySteps(SemaphoreAccessKeysApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create no-auth access key in Semaphore project {projectId}")
  public AccessKey create(long projectId, AccessKeyRequest request) {
    AccessKey key = Calls.body(api.createAccessKey(projectId, request), 201, "created access key");
    teardown.push(
        "Delete Semaphore access key " + key.id(),
        () -> Calls.expectStatus(api.deleteAccessKey(projectId, key.id()), 204));
    return key;
  }
}
