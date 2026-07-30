package org.sequeless.filter.sql.api;

import org.sequeless.filter.api.AnyFilterExpander;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.FilterValidator;
import org.sequeless.filter.api.FilterVisitor;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.sql.spi.QueryBuilder;

/**
 * Translates a validated {@link FilterNode} AST into a single {@link SqlFragment}, delegating
 * every vendor-specific rendering decision to an injected {@link QueryBuilder}.
 *
 * <p>This is the vendor-agnostic core of the hexagonal design: it walks the tree (as a
 * {@link FilterVisitor}), resolves each field path via a {@link ColumnMapping}, converts each
 * {@link com.fasterxml.jackson.databind.JsonNode} value to a plain Java value, and hands the
 * result to the supplied {@link QueryBuilder} port — but never renders SQL text itself.
 *
 * <p>{@link #translate} performs, in order:
 *
 * <ol>
 *   <li>Validates {@code node} against {@code fields}/{@code ops} via
 *       {@link FilterValidator#validate}. {@code translate} does not assume its input was
 *       validated upstream — nothing else in this library calls {@code FilterValidator}
 *       automatically, so this is the only place that guarantee is actually enforced for callers
 *       of the SQL adapter. Any reported violation is converted to a {@link SqlRenderException}.
 *   <li>Walks the tree. For a {@link org.sequeless.filter.api.FieldFilter}, resolves
 *       {@code path} via {@code columns}, raising {@link SqlRenderException} if unmapped, converts
 *       {@code value} to a plain Java object, and dispatches to {@link QueryBuilder#binary} or
 *       {@link QueryBuilder#unary} — by the resolved operator's
 *       {@link org.sequeless.filter.api.OperatorDefinition#isUnary()}, <em>never</em> by whether
 *       the converted value is {@code null} (a null-valued binary condition, e.g.
 *       {@code status is null}, and a genuinely unary condition are otherwise indistinguishable
 *       after conversion).
 *   <li>For an {@link org.sequeless.filter.api.AnyFilter}, expands it in place — inside the visit
 *       for that node, not via one whole-tree {@link AnyFilterExpander#expand(FilterNode,
 *       FieldRegistry, OperatorRegistry)} call up front — using {@code AnyFilterExpander}'s default
 *       expansion strategy. Because the expansion happens exactly where the {@code AnyFilter} node
 *       is visited, the resulting operands are known at that point to be expansion-derived; any
 *       operand whose path has no {@code ColumnMapping} entry is silently dropped rather than
 *       failing the whole filter, and {@link SqlRenderException} is raised only if none of the
 *       expanded operands survive. An explicitly-named path at any other tree position is
 *       unaffected by this leniency — it still hard-fails on an unmapped column.
 *   <li>For an {@link org.sequeless.filter.api.AndFilter}/{@link org.sequeless.filter.api.OrFilter}
 *       at any depth, rejects a zero-operand result with {@link SqlRenderException} (this also
 *       catches an {@code any}-expansion left empty by the previous step's drop-unmapped rule)
 *       before delegating to {@link QueryBuilder#and}/{@link QueryBuilder#or}.
 *   <li>Rejects, with {@link SqlRenderException}, any list-shaped operator (per
 *       {@link org.sequeless.filter.api.OperatorDefinition#getValueShape()} — not the literal
 *       operator name {@code is in}) whose converted value is an empty list.
 * </ol>
 *
 * <p><strong>Custom {@code any} expansion is out of reach here.</strong> This translator always
 * uses {@code AnyFilterExpander}'s default strategy; a caller needing a non-default
 * {@link org.sequeless.filter.api.AnyExpansionStrategy} must pre-expand the filter with their own
 * strategy before calling {@link #translate}.
 *
 * <p><strong>Implementations are expected to be stateless and thread-safe</strong>, the same as
 * {@link QueryBuilder} — callers will naturally hold a single instance as a long-lived singleton.
 */
public interface FilterQueryTranslator {

    /**
     * Translates {@code node} into a single SQL fragment.
     *
     * @param node    the filter AST to translate; may be untrusted or deserialized input — this
     *                method validates it itself and does not assume it was validated upstream
     * @param columns resolves field paths to SQL column names
     * @param fields  field registry used both for {@link FilterValidator} and for
     *                {@code any}-expansion's field-compatibility lookup
     * @param ops     operator registry used both for {@link FilterValidator} and for resolving
     *                each node's operator
     * @param builder vendor port that renders the resolved columns/operators/values to SQL
     * @return the rendered fragment
     * @throws SqlRenderException if {@code node} fails validation, references an unmapped column
     *                             outside an {@code any}-expansion, contains a zero-operand
     *                             {@code and}/{@code or} at any depth, supplies an empty list to a
     *                             list-shaped operator, or contains a value of an unrecognized
     *                             {@code JsonNode} subtype
     */
    SqlFragment translate(
            FilterNode node, ColumnMapping columns, FieldRegistry fields, OperatorRegistry ops, QueryBuilder builder);
}
