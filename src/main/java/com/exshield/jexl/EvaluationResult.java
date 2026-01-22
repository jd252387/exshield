package com.exshield.jexl;

/**
 * Result of a JEXL rule evaluation.
 *
 * @param passed      whether the rule evaluation returned true
 * @param actualValue the actual value extracted by the valueExpression (null if not available)
 */
public record EvaluationResult(boolean passed, Object actualValue) {

    /**
     * Creates a passing result with no value.
     */
    public static EvaluationResult pass() {
        return new EvaluationResult(true, null);
    }

    /**
     * Creates a passing result with a value.
     */
    public static EvaluationResult pass(Object actualValue) {
        return new EvaluationResult(true, actualValue);
    }

    /**
     * Creates a failing result with the actual value.
     */
    public static EvaluationResult fail(Object actualValue) {
        return new EvaluationResult(false, actualValue);
    }

    /**
     * Creates a failing result with no value.
     */
    public static EvaluationResult fail() {
        return new EvaluationResult(false, null);
    }
}
