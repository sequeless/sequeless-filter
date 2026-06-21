package org.sequeless.filter.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.spi.OperatorContributor;

/** Default {@link OperatorRegistry} implementation. Fails fast on duplicate names or aliases. */
public class DefaultOperatorRegistry implements OperatorRegistry {

    private final List<OperatorDefinition> ordered;
    private final Map<String, OperatorDefinition> index;

    public DefaultOperatorRegistry(List<OperatorContributor> contributors) {
        this.ordered = new ArrayList<>();
        this.index = new HashMap<>();

        for (OperatorContributor contributor : contributors) {
            for (OperatorDefinition op : contributor.operators()) {
                register(op.getCanonicalName(), op);
                for (String alias : op.getAliases()) {
                    register(alias, op);
                }
                ordered.add(op);
            }
        }
    }

    private void register(String name, OperatorDefinition op) {
        if (index.containsKey(name)) {
            OperatorDefinition existing = index.get(name);
            throw new IllegalArgumentException(
                    "Duplicate operator name or alias '" + name + "': defined by '"
                            + existing.getCanonicalName() + "' and '" + op.getCanonicalName() + "'");
        }
        index.put(name, op);
    }

    @Override
    public Optional<OperatorDefinition> findByCanonicalOrAlias(String nameOrAlias) {
        return Optional.ofNullable(index.get(nameOrAlias));
    }

    @Override
    public List<OperatorDefinition> all() {
        return List.copyOf(ordered);
    }

    @Override
    public List<OperatorDefinition> applicableTo(String jsonSchemaType, String jsonSchemaFormat) {
        return ordered.stream()
                .filter(op -> typeMatches(op, jsonSchemaType) && formatMatches(op, jsonSchemaFormat))
                .toList();
    }

    private static boolean typeMatches(OperatorDefinition op, String type) {
        return op.getApplicableTypes().isEmpty() || op.getApplicableTypes().contains(type);
    }

    private static boolean formatMatches(OperatorDefinition op, String format) {
        if (op.getApplicableFormats().isEmpty()) return true;
        if (format == null) return false;
        return op.getApplicableFormats().contains(format);
    }
}
