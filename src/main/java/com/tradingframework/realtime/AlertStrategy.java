package com.tradingframework.realtime;

import java.util.Optional;

/** A predefined real-time alert rule. See {@link Alerts} for built-ins. */
public interface AlertStrategy {

    String name();

    /** Restrict the rule to certain symbols; defaults to all watched symbols. */
    default boolean appliesTo(String symbol) {
        return true;
    }

    Optional<Signal> evaluate(AlertContext context);
}
