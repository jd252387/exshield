package com.exshield.config;

import org.apache.solr.common.util.NamedList;

import java.util.Objects;

/**
 * Configuration for a single ExShield rule.
 * Immutable after construction.
 */
public final class RuleConfig {

    private final String name;
    private final String expression;
    private final String message;
    private final String valueExpression;

    private RuleConfig(String name, String expression, String message, String valueExpression) {
        this.name = Objects.requireNonNull(name, "Rule name is required");
        this.expression = Objects.requireNonNull(expression, "Rule expression is required");
        this.message = message;
        this.valueExpression = valueExpression;
    }

    /**
     * Parses a RuleConfig from a NamedList.
     *
     * @param args the NamedList containing rule configuration
     * @return the parsed RuleConfig
     * @throws IllegalArgumentException if required fields are missing
     */
    public static RuleConfig fromNamedList(NamedList<?> args) {
        if (args == null) {
            throw new IllegalArgumentException("Rule configuration cannot be null");
        }

        String name = (String) args.get("name");
        String expression = (String) args.get("expression");
        String message = (String) args.get("message");
        String valueExpression = (String) args.get("valueExpression");

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Rule 'name' is required and cannot be blank");
        }
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Rule 'expression' is required and cannot be blank");
        }

        return new RuleConfig(name.trim(), expression.trim(),
                message != null ? message.trim() : null,
                valueExpression != null ? valueExpression.trim() : null);
    }

    public String getName() {
        return name;
    }

    public String getExpression() {
        return expression;
    }

    public String getMessage() {
        return message;
    }

    public String getValueExpression() {
        return valueExpression;
    }

    public boolean hasValueExpression() {
        return valueExpression != null && !valueExpression.isBlank();
    }

    public boolean hasMessage() {
        return message != null && !message.isBlank();
    }

    @Override
    public String toString() {
        return "RuleConfig{name='" + name + "', expression='" + expression + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuleConfig that = (RuleConfig) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(expression, that.expression) &&
                Objects.equals(message, that.message) &&
                Objects.equals(valueExpression, that.valueExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, expression, message, valueExpression);
    }
}
