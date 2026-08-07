package io.bookwright.api.model.semaphore;

public record UserRequest(
    String name,
    String username,
    String email,
    String password,
    boolean alert,
    boolean admin,
    boolean external) {}
