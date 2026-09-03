package io.bookwright.junit;

import io.bookwright.api.AuthSession;
import io.bookwright.api.model.CreatedBooking;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.SemaphoreTestUser;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.util.TestData;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Typed access to fixture and precondition state owned by the current test method. */
public class TestStore {

  private final ExtensionContext.Store store;

  TestStore(ExtensionContext context) {
    this(NamespaceRegistry.methodStore(context));
  }

  TestStore(ExtensionContext.Store store) {
    this.store = store;
  }

  <T> T getRequired(String key, Class<T> type) {
    T value = store.get(key, type);
    if (value == null) {
      throw new IllegalStateException(
          "No '%s' in the test store. Did you forget the fixture/precondition that provides it?"
              .formatted(key));
    }
    return value;
  }

  void put(String key, Object value) {
    store.put(key, value);
  }

  public AuthSession authSession() {
    return getRequired(AuthSessionExtension.AUTH_SESSION_KEY, AuthSession.class);
  }

  public TestUser testUser() {
    return getRequired(UserFixtureExtension.TEST_USER_KEY, TestUser.class);
  }

  public TestData testData() {
    return getRequired(TestDataExtension.TEST_DATA_KEY, TestData.class);
  }

  public CreatedBooking booking() {
    return getRequired(Precondition.BOOKING_KEY, CreatedBooking.class);
  }

  public SemaphoreTestUser semaphoreRbacUser() {
    return getRequired(Precondition.SEMAPHORE_RBAC_USER_KEY, SemaphoreTestUser.class);
  }

  public Project semaphoreProject() {
    return getRequired(Precondition.SEMAPHORE_PROJECT_KEY, Project.class);
  }

  public Template semaphoreTemplate() {
    return getRequired(Precondition.SEMAPHORE_TEMPLATE_KEY, Template.class);
  }

  void putBooking(CreatedBooking booking) {
    put(Precondition.BOOKING_KEY, booking);
  }

  void putSemaphoreRbacUser(SemaphoreTestUser user) {
    put(Precondition.SEMAPHORE_RBAC_USER_KEY, user);
  }

  void putSemaphoreProject(Project project) {
    put(Precondition.SEMAPHORE_PROJECT_KEY, project);
  }

  void putSemaphoreTemplate(Template template) {
    put(Precondition.SEMAPHORE_TEMPLATE_KEY, template);
  }
}
