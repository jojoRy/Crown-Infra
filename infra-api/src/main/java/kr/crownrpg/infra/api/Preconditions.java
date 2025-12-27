package kr.crownrpg.infra.api;

import java.util.Objects;

/**
 * Simple precondition utilities to validate arguments without any external dependencies.
 */
public final class Preconditions {

    private Preconditions() {
    }

    public static <T> T checkNotNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    public static String checkNotBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static <T> T requireNonNull(T value, String message) {
        return Objects.requireNonNull(value, message);
    }
}
