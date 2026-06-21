package org.sequeless.filter.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;

/** Default strict {@link FieldRegistry} implementation. */
public class DefaultFieldRegistry implements FieldRegistry {

    private final List<FieldDefinition> ordered;
    private final Map<String, FieldDefinition> index;

    public DefaultFieldRegistry(List<FieldDefinition> fields) {
        this.ordered = new ArrayList<>(fields);
        this.index = new LinkedHashMap<>();
        for (FieldDefinition field : fields) {
            index.put(field.getPath(), field);
        }
    }

    @Override
    public Optional<FieldDefinition> find(String path) {
        return Optional.ofNullable(index.get(path));
    }

    @Override
    public List<FieldDefinition> all() {
        return List.copyOf(ordered);
    }

    @Override
    public List<FieldDefinition> compatibleWith(String canonicalOperator, OperatorRegistry operators) {
        return ordered.stream()
                .filter(f -> isCompatible(f, canonicalOperator, operators))
                .toList();
    }

    private static boolean isCompatible(FieldDefinition field, String canonicalOperator, OperatorRegistry operators) {
        List<String> permitted = field.getPermittedOperators();
        if (!permitted.isEmpty()) {
            return permitted.contains(canonicalOperator);
        }
        return operators
                .findByCanonicalOrAlias(canonicalOperator)
                .map(op -> typeMatches(op, field.getJsonSchemaType()))
                .orElse(false);
    }

    private static boolean typeMatches(OperatorDefinition op, String fieldType) {
        return op.getApplicableTypes().isEmpty() || op.getApplicableTypes().contains(fieldType);
    }
}
