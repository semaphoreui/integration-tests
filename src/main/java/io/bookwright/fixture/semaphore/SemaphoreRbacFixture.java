package io.bookwright.fixture.semaphore;

import io.bookwright.api.model.semaphore.SemaphoreTestUser;
import io.bookwright.api.model.semaphore.User;
import io.bookwright.api.model.semaphore.UserRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SemaphoreRbacFixture {

  public static final String STORE_KEY = "semaphoreRbacUser";
  public static final String USERNAME = "bookwright-rbac-guest";
  private static final String PASSWORD = "Bookwright-test-password-42!";

  public UserRequest userRequest() {
    return new UserRequest(
        "Bookwright Guest", USERNAME, USERNAME + "@localhost", PASSWORD, false, false, false);
  }

  public SemaphoreTestUser account(User user) {
    return new SemaphoreTestUser(user, PASSWORD);
  }
}
