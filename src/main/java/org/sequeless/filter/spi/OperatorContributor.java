package org.sequeless.filter.spi;

import java.util.List;
import org.sequeless.filter.api.OperatorDefinition;

/**
 * Extension point for registering custom operators.
 * Implement and pass to {@link org.sequeless.filter.api.OperatorRegistry#of(List)}.
 */
@FunctionalInterface
public interface OperatorContributor {

    List<OperatorDefinition> operators();
}
