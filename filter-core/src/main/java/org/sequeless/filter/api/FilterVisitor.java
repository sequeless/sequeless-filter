package org.sequeless.filter.api;

/**
 * Abstract visitor for traversing and transforming a {@link FilterNode} tree.
 * Extend this class to implement AST transformations.
 *
 * @param <T> the result type produced for each node
 */
public abstract class FilterVisitor<T> {

    /** Called for each {@link FieldFilter} node. */
    public abstract T visitField(FieldFilter node);

    /** Called for each {@link AnyFilter} node. */
    public abstract T visitAny(AnyFilter node);

    /** Called for each {@link AndFilter} node. */
    public abstract T visitAnd(AndFilter node);

    /** Called for each {@link OrFilter} node. */
    public abstract T visitOr(OrFilter node);

    /**
     * Dispatches to the correct {@code visit*} method based on runtime type.
     *
     * @param node the node to visit
     * @return the result produced by the matching visit method
     */
    public final T walk(FilterNode node) {
        return switch (node) {
            case FieldFilter f -> visitField(f);
            case AnyFilter a -> visitAny(a);
            case AndFilter a -> visitAnd(a);
            case OrFilter o -> visitOr(o);
        };
    }
}
