package io.bookwright.junit;

import io.bookwright.api.model.CreatedBooking;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.SemaphoreAuthLifecycleUsers;
import io.bookwright.api.model.semaphore.SemaphoreTaskHistory;
import io.bookwright.api.model.semaphore.SemaphoreTestUser;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.api.model.semaphore.User;
import io.bookwright.config.Configs;
import io.bookwright.fixtures.semaphore.SemaphoreAuthLifecycleFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreProjectIsolationFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreTaskHistoryFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreTokenFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreTotpFixtures;
import io.bookwright.steps.ApiSteps;
import io.bookwright.util.TestData;
import java.util.List;
import java.util.function.BiConsumer;

/** Catalog of product-state preconditions shared through typed {@link TestStore} accessors. */
public enum Precondition implements IPrecondition {
  BOOKING_EXISTS(
      "Create a booking",
      (api, store) -> {
        TestData data = store.testData();
        CreatedBooking created =
            api.restfulBooker()
                .bookings()
                .create(data.booking(), api.restfulBooker().auth().session());
        store.putBooking(created);
      }),

  SEMAPHORE_ADMIN_SESSION(
      "Login to Semaphore as administrator", (api, store) -> api.semaphore().auth().login()),

  SEMAPHORE_TASK_HISTORY_EXISTS(
      "Create completed, interleaved task history for two templates",
      (api, store) -> {
        var template = store.semaphoreTemplate();
        var fixtures = SemaphoreTaskHistoryFixtures.from(store.testData());
        var sibling =
            api.semaphore()
                .templates()
                .create(
                    template.projectId(),
                    fixtures
                        .siblingTemplate()
                        .request(
                            template.projectId(), template.repositoryId(), template.inventoryId()));
        var oldest = api.semaphore().tasks().startAndWait(template.projectId(), template.id());
        var other = api.semaphore().tasks().startAndWait(template.projectId(), sibling.id());
        var middle = api.semaphore().tasks().startAndWait(template.projectId(), template.id());
        var newest = api.semaphore().tasks().startAndWait(template.projectId(), template.id());
        store.putSemaphoreTaskHistory(
            new SemaphoreTaskHistory(List.of(newest, middle, other, oldest), template.id()));
      }),

  SEMAPHORE_PROJECT_EXISTS(
      "Create an isolated Semaphore project",
      (api, store) -> {
        SemaphoreFixtures fixtures = SemaphoreFixtures.from(Configs.main(), store.testData());
        Project project = api.semaphore().projects().createProject(fixtures.projects().secrets());
        store.putSemaphoreProject(project);
      }),

  SEMAPHORE_ACCESS_KEY_EXISTS(
      "Create a no-auth Semaphore access key",
      (api, store) -> {
        SemaphoreFixtures fixtures = SemaphoreFixtures.from(Configs.main(), store.testData());
        long projectId = store.semaphoreProject().id();
        store.putSemaphoreAccessKey(
            api.semaphore()
                .accessKeys()
                .create(projectId, fixtures.accessKey().request(projectId)));
      }),

  SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS(
      "Create an executable Semaphore template",
      (api, store) -> {
        SemaphoreFixtures fixtures = SemaphoreFixtures.from(Configs.main(), store.testData());
        Project project = store.semaphoreProject();
        var key =
            api.semaphore()
                .accessKeys()
                .create(project.id(), fixtures.accessKey().request(project.id()));
        var repository =
            api.semaphore()
                .repositories()
                .create(
                    project.id(),
                    fixtures.repositories().primary().request(project.id(), key.id()));
        var inventory =
            api.semaphore()
                .inventories()
                .create(project.id(), fixtures.inventory().request(project.id(), key.id()));
        Template template =
            api.semaphore()
                .templates()
                .create(
                    project.id(),
                    fixtures
                        .templates()
                        .primary()
                        .request(project.id(), repository.id(), inventory.id()));
        store.putSemaphoreTemplate(template);
      }),

  SEMAPHORE_TOTP_API_USER_EXISTS(
      "Create a disposable Semaphore TOTP API user",
      (api, store) ->
          store.putSemaphoreTotpUser(
              api.semaphore()
                  .users()
                  .createDisposable(
                      SemaphoreTotpFixtures.from(store.testData()).apiAccount().userRequest()))),

  SEMAPHORE_TOTP_UI_USER_EXISTS(
      "Create a disposable Semaphore TOTP UI user and register binding cleanup",
      (api, store) -> {
        User user =
            api.semaphore()
                .users()
                .createDisposable(
                    SemaphoreTotpFixtures.from(store.testData()).uiAccount().userRequest());
        store.putSemaphoreTotpUser(user);
        api.semaphore().users().disableTotpAfterTest(user);
      }),

  SEMAPHORE_PROJECT_ISOLATION_ACTOR_EXISTS(
      "Create a disposable non-admin owner of a separate Semaphore project",
      (api, store) -> {
        var fixtures = SemaphoreProjectIsolationFixtures.from(store.testData());
        var user = api.semaphore().users().createDisposable(fixtures.user());
        store.putSemaphoreIsolationUser(new SemaphoreTestUser(user, fixtures.user().password()));
        var project = api.semaphore().projects().createProject(fixtures.accessibleProject());
        store.putSemaphoreAccessibleProject(project);
        api.semaphore().users().addToProject(project.id(), user.id(), fixtures.role());
      }),

  SEMAPHORE_TOKEN_OWNERS_EXIST(
      "Create two disposable non-admin API-token owners",
      (api, store) -> {
        var fixtures = SemaphoreTokenFixtures.from(store.testData());
        var owner = api.semaphore().users().createDisposable(fixtures.ownerRequest());
        store.putSemaphoreTokenOwner(
            new SemaphoreTestUser(owner, fixtures.ownerRequest().password()));
        var other = api.semaphore().users().createDisposable(fixtures.otherOwnerRequest());
        store.putSemaphoreOtherTokenOwner(
            new SemaphoreTestUser(other, fixtures.otherOwnerRequest().password()));
      }),

  SEMAPHORE_RBAC_USER_EXISTS(
      "Ensure the Semaphore RBAC fixture user exists",
      (api, store) -> {
        SemaphoreFixtures.Rbac rbac =
            SemaphoreFixtures.from(Configs.main(), store.testData()).rbac();
        User user = api.semaphore().users().getOrCreate(rbac.userRequest());
        store.putSemaphoreRbacUser(rbac.account(user));
      }),

  SEMAPHORE_AUTH_LIFECYCLE_USERS_EXIST(
      "Ensure reusable Semaphore authentication lifecycle users exist",
      (api, store) -> {
        SemaphoreAuthLifecycleFixtures fixtures = SemaphoreAuthLifecycleFixtures.standard();
        store.putSemaphoreAuthLifecycleUsers(
            new SemaphoreAuthLifecycleUsers(
                fixtures
                    .actor()
                    .account(
                        api.semaphore()
                            .users()
                            .ensurePasswordFixture(
                                fixtures.actor().userRequest(),
                                fixtures.actor().baselinePassword())),
                fixtures
                    .target()
                    .account(
                        api.semaphore()
                            .users()
                            .ensurePasswordFixture(
                                fixtures.target().userRequest(),
                                fixtures.target().baselinePassword()))));
      });

  static final String BOOKING_KEY = "createdBooking";
  static final String SEMAPHORE_TASK_HISTORY_KEY = "semaphoreTaskHistory";
  static final String SEMAPHORE_PROJECT_KEY = "semaphoreProject";
  static final String SEMAPHORE_ACCESSIBLE_PROJECT_KEY = "semaphoreAccessibleProject";
  static final String SEMAPHORE_ISOLATION_USER_KEY = "semaphoreIsolationUser";
  static final String SEMAPHORE_ACCESS_KEY_KEY = "semaphoreAccessKey";
  static final String SEMAPHORE_TEMPLATE_KEY = "semaphoreTemplate";
  static final String SEMAPHORE_TOTP_USER_KEY = "semaphoreTotpUser";
  static final String SEMAPHORE_RBAC_USER_KEY = "semaphoreRbacUser";
  static final String SEMAPHORE_AUTH_LIFECYCLE_USERS_KEY = "semaphoreAuthLifecycleUsers";
  static final String SEMAPHORE_TOKEN_OWNER_KEY = "semaphoreTokenOwner";
  static final String SEMAPHORE_OTHER_TOKEN_OWNER_KEY = "semaphoreOtherTokenOwner";

  private final String title;
  private final BiConsumer<ApiSteps, TestStore> action;

  Precondition(String title, BiConsumer<ApiSteps, TestStore> action) {
    this.title = title;
    this.action = action;
  }

  @Override
  public String title() {
    return title;
  }

  @Override
  public void execute(ApiSteps api, TestStore store) {
    action.accept(api, store);
  }
}
