package org.sequeless.filter.spi;

import java.util.List;
import org.sequeless.filter.api.FieldDefinition;

/**
 * Extension point for registering filterable fields.
 * Implement and pass to {@link org.sequeless.filter.api.FieldRegistry#of(List)}.
 */
@FunctionalInterface
public interface FieldContributor {

    List<FieldDefinition> fields();
}
