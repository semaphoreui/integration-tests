package io.bookwright.api.semaphore;

import io.bookwright.api.model.semaphore.ProjectMemberRequest;
import io.bookwright.api.model.semaphore.User;
import io.bookwright.api.model.semaphore.UserRequest;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreUsersApi {

  @POST("users")
  Call<User> createUser(@Body UserRequest request);

  @GET("users")
  Call<List<User>> getUsers();

  @DELETE("users/{userId}")
  Call<Void> deleteUser(@Path("userId") long userId);

  @POST("project/{projectId}/users")
  Call<Void> addProjectUser(@Path("projectId") long projectId, @Body ProjectMemberRequest request);

  @DELETE("project/{projectId}/users/{userId}")
  Call<Void> removeProjectUser(@Path("projectId") long projectId, @Path("userId") long userId);
}
