package org.sequeless.filter.spi;

import java.util.List;
import org.sequeless.filter.api.FieldDefinition;

/**
 * Extension point for registering filterable fields.
 *
 * <p>Not consumed automatically: pass the contributed definitions into
 * {@link org.sequeless.filter.api.FieldRegistrySpec}'s {@code fields} property and build a registry
 * with {@link org.sequeless.filter.api.FieldRegistry#of(org.sequeless.filter.api.FieldRegistrySpec)}.
 */
@FunctionalInterface
public interface FieldContributor {

    List<FieldDefinition> fields();
}
