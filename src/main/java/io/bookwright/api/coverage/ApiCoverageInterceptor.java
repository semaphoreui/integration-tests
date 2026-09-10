package io.bookwright.api.coverage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class ApiCoverageInterceptor implements Interceptor {

  public static final String OUTPUT_PROPERTY = "bookwright.api.coverage.file";
  private static final Object FILE_LOCK = new Object();

  private final Path output;

  public ApiCoverageInterceptor(Path output) {
    this.output = output;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    String apiPath = ApiPaths.apiPath(request.url().encodedPath());
    try {
      Response response = chain.proceed(request);
      record(apiPath, request.method(), response.code());
      return response;
    } catch (IOException error) {
      record(apiPath, request.method(), 0);
      throw error;
    }
  }

  private void record(String apiPath, String method, int status) throws IOException {
    if (apiPath == null) {
      return;
    }
    synchronized (FILE_LOCK) {
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(
          output,
          new ApiCoverageObservation(new ApiOperation(method, apiPath), status).serialize(),
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    }
  }
}
