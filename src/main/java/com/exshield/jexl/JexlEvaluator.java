package com.exshield.jexl;

import com.exshield.config.RuleConfig;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates JEXL expressions against RequestAnalysis data.
 * Thread-safe with expression caching.
 */
public class JexlEvaluator {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final JexlEngine engine;
    private final ConcurrentHashMap<String, JexlExpression> expressionCache;
    private final int cacheSize;

    /**
     * Creates a new JexlEvaluator with the specified cache size.
     *
     * @param cacheSize maximum number of expressions to cache
     */
    public JexlEvaluator(int cacheSize) {
        this.cacheSize = cacheSize;
        this.expressionCache = new ConcurrentHashMap<>();
        this.engine = new JexlBuilder()
                .cache(cacheSize)
                .silent(false)
                .strict(false)
                .safe(false)
                .permissions(JexlPermissions.UNRESTRICTED)
                .create();
    }

    /**
     * Evaluates a rule against the provided analysis data.
     *
     * @param rule            the rule to evaluate
     * @param queryAnalysis   map of analysis data from RequestAnalysis.queryAnalysis()
     * @param filtersAnalysis map of filter analysis from RequestAnalysis.filtersAnalysis() (may be null)
     * @param mergedAnalysis  map of merged analysis from Solr context (may be null)
     * @return the evaluation result
     * @throws JexlEvaluationException if evaluation fails
     */
    public EvaluationResult evaluate(RuleConfig rule, Map<String, Object> queryAnalysis,
                                     Map<String, Object> filtersAnalysis,
                                     Map<String, Object> mergedAnalysis) throws JexlEvaluationException {
        JexlContext context = createContext(queryAnalysis, filtersAnalysis, mergedAnalysis);

        // Evaluate the main expression
        boolean passed;
        try {
            JexlExpression expr = getOrCompile(rule.getExpression());
            Object result = expr.evaluate(context);
            passed = toBoolean(result);
        } catch (JexlException e) {
            throw new JexlEvaluationException("Failed to evaluate expression '" + rule.getExpression() +
                    "' for rule '" + rule.getName() + "'", e);
        }

        // Extract actual value if valueExpression is provided
        Object actualValue = null;
        if (rule.hasValueExpression()) {
            try {
                JexlExpression valueExpr = getOrCompile(rule.getValueExpression());
                actualValue = valueExpr.evaluate(context);
            } catch (JexlException e) {
                log.warn("Failed to evaluate valueExpression '{}' for rule '{}': {}",
                        rule.getValueExpression(), rule.getName(), e.getMessage());
                // Don't fail the whole evaluation just because valueExpression failed
            }
        }

        return passed ? EvaluationResult.pass(actualValue) : EvaluationResult.fail(actualValue);
    }

    /**
     * Creates a JexlContext from the analysis data.
     * queryAnalysis is exposed as 'query'.
     * filtersAnalysis is exposed as 'filters'.
     * mergedAnalysis is exposed as 'total'.
     */
    private JexlContext createContext(Map<String, Object> queryAnalysis,
                                      Map<String, Object> filtersAnalysis,
                                      Map<String, Object> mergedAnalysis) {
        MapContext context = new MapContext();

        // Add queryAnalysis as 'query'
        context.set("query", queryAnalysis);

        // Add filtersAnalysis as 'filters'
        context.set("filters", filtersAnalysis);

        // Add mergedAnalysis as 'total'
        context.set("total", mergedAnalysis);

        return context;
    }

    /**
     * Gets a compiled expression from cache or compiles it.
     */
    private JexlExpression getOrCompile(String expression) {
        return expressionCache.computeIfAbsent(expression, expr -> {
            // Evict if cache is full (simple eviction strategy)
            if (expressionCache.size() >= cacheSize) {
                // Remove first entry (arbitrary eviction)
                expressionCache.keySet().stream().findFirst()
                        .ifPresent(expressionCache::remove);
            }
            return engine.createExpression(expr);
        });
    }

    /**
     * Converts a JEXL result to boolean.
     */
    private boolean toBoolean(Object result) {
        if (result == null) {
            return false;
        }
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        // JEXL might return other truthy values
        return Boolean.parseBoolean(result.toString());
    }

    /**
     * Exception thrown when JEXL evaluation fails.
     */
    public static class JexlEvaluationException extends Exception {
        public JexlEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
