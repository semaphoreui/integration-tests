package io.bookwright.junit;

import io.bookwright.api.model.CreatedBooking;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.api.model.semaphore.User;
import io.bookwright.config.Configs;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.steps.ApiSteps;
import io.bookwright.util.TestData;
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

  SEMAPHORE_PROJECT_EXISTS(
      "Create an isolated Semaphore project",
      (api, store) -> {
        SemaphoreFixtures fixtures = SemaphoreFixtures.from(Configs.main(), store.testData());
        Project project = api.semaphore().projects().createProject(fixtures.projects().secrets());
        store.putSemaphoreProject(project);
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

  SEMAPHORE_RBAC_USER_EXISTS(
      "Ensure the Semaphore RBAC fixture user exists",
      (api, store) -> {
        SemaphoreFixtures.Rbac rbac =
            SemaphoreFixtures.from(Configs.main(), store.testData()).rbac();
        User user = api.semaphore().users().getOrCreate(rbac.userRequest());
        store.putSemaphoreRbacUser(rbac.account(user));
      });

  static final String BOOKING_KEY = "createdBooking";
  static final String SEMAPHORE_PROJECT_KEY = "semaphoreProject";
  static final String SEMAPHORE_TEMPLATE_KEY = "semaphoreTemplate";
  static final String SEMAPHORE_RBAC_USER_KEY = "semaphoreRbacUser";

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
