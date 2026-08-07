package io.bookwright.api.semaphore.schedules;

import io.bookwright.api.model.semaphore.Schedule;
import io.bookwright.api.model.semaphore.ScheduleRequest;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreSchedulesApi {

  @POST("project/{projectId}/schedules")
  Call<Schedule> createSchedule(@Path("projectId") long projectId, @Body ScheduleRequest request);

  @GET("project/{projectId}/schedules")
  Call<List<Schedule>> getSchedules(@Path("projectId") long projectId);

  @GET("project/{projectId}/schedules/{scheduleId}")
  Call<Schedule> getSchedule(
      @Path("projectId") long projectId, @Path("scheduleId") long scheduleId);

  @DELETE("project/{projectId}/schedules/{scheduleId}")
  Call<Void> deleteSchedule(@Path("projectId") long projectId, @Path("scheduleId") long scheduleId);
}
