package org.sequeless.filter.api;

/**
 * Describes a single validation problem found by {@link FilterValidator}.
 *
 * @param path    the field path at which the violation was detected, or {@code null} for
 *                node-level issues (e.g. an {@link AnyFilter} with an unknown operator)
 * @param message human-readable description of the problem
 */
public record FilterViolation(String path, String message) {}
