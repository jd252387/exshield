package com.exshield.jexl;

import com.exshield.config.RuleConfig;
import com.exshield.jexl.JexlEvaluator.JexlEvaluationException;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JexlEvaluatorTest {

    private JexlEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new JexlEvaluator(100);
    }

    @Test
    void testSimpleComparisonPasses() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "query.count <= 100", null, null);
        Map<String, Object> queryAnalysis = Map.of("count", 50);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);

        assertTrue(result.passed());
    }

    @Test
    void testSimpleComparisonFails() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "query.count <= 100", null, null);
        Map<String, Object> queryAnalysis = Map.of("count", 150);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);

        assertFalse(result.passed());
    }

    @Test
    void testMethodCallOnObject() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "query.stats.getCount() <= 100", null, null);
        MockStats stats = new MockStats(50);
        Map<String, Object> queryAnalysis = Map.of("stats", stats);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);

        assertTrue(result.passed());
    }

    @Test
    void testMethodCallOnObjectFails() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "query.stats.getCount() <= 100", null, null);
        MockStats stats = new MockStats(150);
        Map<String, Object> queryAnalysis = Map.of("stats", stats);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);

        assertFalse(result.passed());
    }

    @Test
    void testValueExpressionExtraction() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "query.count <= 100", "query.count", null);
        Map<String, Object> queryAnalysis = Map.of("count", 150);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);

        assertFalse(result.passed());
        assertEquals(150, result.actualValue());
    }

    @Test
    void testFiltersAccessible() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "filters == null || filters['count'] <= 100", null, null);
        Map<String, Object> queryAnalysis = Map.of("count", 50);
        Map<String, Object> filtersAnalysis = Map.of("count", 75);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, filtersAnalysis, null);

        assertTrue(result.passed());
    }

    @Test
    void testFiltersAccessibleWhenNull() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "filters == null || filters['count'] <= 100", null, null);
        Map<String, Object> queryAnalysis = Map.of("count", 50);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);

        assertTrue(result.passed());
    }

    @Test
    void testComplexExpression() throws JexlEvaluationException {
        RuleConfig rule = createRule("test",
                "query.clause_statistics.getTermCount() <= 1000 && query.clause_statistics.getBlockClauseCount() <= 50",
                null, null);
        MockClauseStatistics stats = new MockClauseStatistics(500, 25);
        Map<String, Object> queryAnalysis = Map.of("clause_statistics", stats);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);

        assertTrue(result.passed());
    }

    @Test
    void testComplexExpressionFailsFirstCondition() throws JexlEvaluationException {
        RuleConfig rule = createRule("test",
                "query.clause_statistics.getTermCount() <= 1000 && query.clause_statistics.getBlockClauseCount() <= 50",
                null, null);
        MockClauseStatistics stats = new MockClauseStatistics(1500, 25);
        Map<String, Object> queryAnalysis = Map.of("clause_statistics", stats);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);

        assertFalse(result.passed());
    }

    @Test
    void testComplexExpressionFailsSecondCondition() throws JexlEvaluationException {
        RuleConfig rule = createRule("test",
                "query.clause_statistics.getTermCount() <= 1000 && query.clause_statistics.getBlockClauseCount() <= 50",
                null, null);
        MockClauseStatistics stats = new MockClauseStatistics(500, 100);
        Map<String, Object> queryAnalysis = Map.of("clause_statistics", stats);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);

        assertFalse(result.passed());
    }

    @Test
    void testInvalidExpressionThrowsException() {
        RuleConfig rule = createRule("test", "invalid syntax {{}", null, null);
        Map<String, Object> queryAnalysis = new HashMap<>();

        assertThrows(JexlEvaluationException.class, () ->
                evaluator.evaluate(rule, queryAnalysis, null, null));
    }

    @Test
    void testNullQueryAnalysis() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "query == null", null, null);

        EvaluationResult result = evaluator.evaluate(rule, null, null, null);

        assertTrue(result.passed());
    }

    @Test
    void testCachingWorks() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "query.count <= 100", null, null);
        Map<String, Object> queryAnalysis = Map.of("count", 50);

        // Evaluate multiple times to trigger caching
        for (int i = 0; i < 10; i++) {
            EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);
            assertTrue(result.passed());
        }
    }

    @Test
    void testValueExpressionFailureDoesNotFailEvaluation() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "query.count <= 100", "nonexistent.getX()", null);
        Map<String, Object> queryAnalysis = Map.of("count", 150);

        // Should not throw, but actualValue should be null
        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);

        assertFalse(result.passed());
        assertNull(result.actualValue());
    }

    @Test
    void testTotalAccessible() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "total.totalCount <= 200", null, null);
        Map<String, Object> queryAnalysis = Map.of("count", 50);
        Map<String, Object> filtersAnalysis = Map.of("count", 75);
        Map<String, Object> mergedAnalysis = Map.of("totalCount", 125);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, filtersAnalysis, mergedAnalysis);

        assertTrue(result.passed());
    }

    @Test
    void testTotalAccessibleWhenNull() throws JexlEvaluationException {
        RuleConfig rule = createRule("test", "total == null || total.totalCount <= 200", null, null);
        Map<String, Object> queryAnalysis = Map.of("count", 50);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, null, null);

        assertTrue(result.passed());
    }

    @Test
    void testCombinedQueryFiltersTotal() throws JexlEvaluationException {
        RuleConfig rule = createRule("test",
                "query.count <= 100 && filters.count <= 100 && total.totalCount <= 200",
                null, null);
        Map<String, Object> queryAnalysis = Map.of("count", 50);
        Map<String, Object> filtersAnalysis = Map.of("count", 75);
        Map<String, Object> mergedAnalysis = Map.of("totalCount", 125);

        EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, filtersAnalysis, mergedAnalysis);

        assertTrue(result.passed());
    }

    private RuleConfig createRule(String name, String expression, String valueExpression, String message) {
        NamedList<String> args = new NamedList<>();
        args.add("name", name);
        args.add("expression", expression);
        if (valueExpression != null) {
            args.add("valueExpression", valueExpression);
        }
        if (message != null) {
            args.add("message", message);
        }
        return RuleConfig.fromNamedList(args);
    }

    // Mock classes for testing
    public static class MockStats {
        private final int count;

        public MockStats(int count) {
            this.count = count;
        }

        public int getCount() {
            return count;
        }
    }

    public static class MockClauseStatistics {
        private final int termCount;
        private final int blockClauseCount;

        public MockClauseStatistics(int termCount, int blockClauseCount) {
            this.termCount = termCount;
            this.blockClauseCount = blockClauseCount;
        }

        public int getTermCount() {
            return termCount;
        }

        public int getBlockClauseCount() {
            return blockClauseCount;
        }
    }
}
