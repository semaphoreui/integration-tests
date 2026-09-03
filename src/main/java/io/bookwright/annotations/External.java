package io.bookwright.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/** Marks read-only scenarios that are safe to run against a user-managed Semaphore instance. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Tag(TestTags.EXTERNAL)
public @interface External {}
