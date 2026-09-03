package io.bookwright.api.model.semaphore;

public record User(
    long id,
    String name,
    String username,
    String email,
    boolean alert,
    boolean admin,
    boolean external,
    UserTotp totp) {}
