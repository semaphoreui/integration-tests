package io.bookwright.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/** Marks the explicit one-time setup of persistent resources on a managed external stand. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Tag(TestTags.EXTERNAL_MANAGED_SETUP)
public @interface ExternalManagedSetup {}
