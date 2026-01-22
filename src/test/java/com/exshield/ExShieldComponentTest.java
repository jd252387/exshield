package com.exshield;

import com.exshield.ExShieldComponent.RequestAnalysis;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExShieldComponentTest {

    @Mock
    private ResponseBuilder responseBuilder;

    @Mock
    private SolrQueryRequest solrRequest;

    private Map<Object, Object> requestContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        requestContext = new HashMap<>();
        // Set public field directly (can't use when() on fields)
        responseBuilder.req = solrRequest;
        when(solrRequest.getContext()).thenReturn(requestContext);
    }

    @Test
    void testPreparePassesWhenRulePasses() throws IOException {
        ExShieldComponent component = createComponent(
                createRuleConfig("test-rule", "query.count <= 100", null, null)
        );

        setAnalysis(Map.of("count", 50), null);
        setSolrParams(Map.of());

        assertDoesNotThrow(() -> component.prepare(responseBuilder));
    }

    @Test
    void testPrepareThrowsWhenRuleFails() {
        ExShieldComponent component = createComponent(
                createRuleConfig("max-count", "query.count <= 100", "query.count", "Count must not exceed 100")
        );

        setAnalysis(Map.of("count", 150), null);
        setSolrParams(Map.of());

        SolrException exception = assertThrows(SolrException.class,
                () -> component.prepare(responseBuilder));

        assertEquals(SolrException.ErrorCode.BAD_REQUEST.code, exception.code());
        assertTrue(exception.getMessage().contains("max-count"));
        assertTrue(exception.getMessage().contains("query.count <= 100"));
        assertTrue(exception.getMessage().contains("150"));
        assertTrue(exception.getMessage().contains("Count must not exceed 100"));
    }

    @Test
    void testPrepareSkipsWhenBypassRequested() throws IOException {
        ExShieldComponent component = createComponentWithBypass(
                createRuleConfig("test-rule", "query.count <= 100", null, null)
        );

        setAnalysis(Map.of("count", 150), null); // Would fail
        setSolrParams(Map.of("exshield.bypass", "true"));

        assertDoesNotThrow(() -> component.prepare(responseBuilder));
    }

    @Test
    void testPrepareDoesNotBypassWhenNotAllowed() {
        ExShieldComponent component = createComponent(
                createRuleConfig("test-rule", "query.count <= 100", null, null)
        );

        setAnalysis(Map.of("count", 150), null);
        setSolrParams(Map.of("exshield.bypass", "true")); // Bypass not allowed

        assertThrows(SolrException.class, () -> component.prepare(responseBuilder));
    }

    @Test
    void testPrepareSkipsWhenNoAnalysis() throws IOException {
        ExShieldComponent component = createComponent(
                createRuleConfig("test-rule", "query.count <= 100", null, null)
        );

        // No analysis set
        setSolrParams(Map.of());

        assertDoesNotThrow(() -> component.prepare(responseBuilder));
    }

    @Test
    void testPrepareFailsOnMissingAnalysisWhenConfigured() {
        ExShieldComponent component = createComponentFailOnMissing(
                createRuleConfig("test-rule", "query.count <= 100", null, null)
        );

        // No analysis set
        setSolrParams(Map.of());

        SolrException exception = assertThrows(SolrException.class,
                () -> component.prepare(responseBuilder));

        assertEquals(SolrException.ErrorCode.SERVER_ERROR.code, exception.code());
        assertTrue(exception.getMessage().contains("RequestAnalysis not found"));
    }

    @Test
    void testPrepareWithMultipleRulesAllPass() throws IOException {
        ExShieldComponent component = createComponent(
                createRuleConfig("rule1", "query.count <= 100", null, null),
                createRuleConfig("rule2", "query.size <= 1000", null, null)
        );

        setAnalysis(Map.of("count", 50, "size", 500), null);
        setSolrParams(Map.of());

        assertDoesNotThrow(() -> component.prepare(responseBuilder));
    }

    @Test
    void testPrepareWithMultipleRulesFirstFails() {
        ExShieldComponent component = createComponent(
                createRuleConfig("rule1", "query.count <= 100", null, null),
                createRuleConfig("rule2", "query.size <= 1000", null, null)
        );

        setAnalysis(Map.of("count", 150, "size", 500), null);
        setSolrParams(Map.of());

        SolrException exception = assertThrows(SolrException.class,
                () -> component.prepare(responseBuilder));

        assertTrue(exception.getMessage().contains("rule1"));
    }

    @Test
    void testPrepareWithMultipleRulesSecondFails() {
        ExShieldComponent component = createComponent(
                createRuleConfig("rule1", "query.count <= 100", null, null),
                createRuleConfig("rule2", "query.size <= 1000", null, null)
        );

        setAnalysis(Map.of("count", 50, "size", 1500), null);
        setSolrParams(Map.of());

        SolrException exception = assertThrows(SolrException.class,
                () -> component.prepare(responseBuilder));

        assertTrue(exception.getMessage().contains("rule2"));
    }

    @Test
    void testPrepareWithMethodCalls() throws IOException {
        ExShieldComponent component = createComponent(
                createRuleConfig("test-rule", "query.stats.getCount() <= 100", null, null)
        );

        MockStats stats = new MockStats(50);
        setAnalysis(Map.of("stats", stats), null);
        setSolrParams(Map.of());

        assertDoesNotThrow(() -> component.prepare(responseBuilder));
    }

    @Test
    void testPrepareWithFiltersAnalysis() throws IOException {
        ExShieldComponent component = createComponent(
                createRuleConfig("test-rule", "filters == null || filters['count'] <= 100", null, null)
        );

        setAnalysis(Map.of("someKey", "test"), Map.of("count", 50));
        setSolrParams(Map.of());

        assertDoesNotThrow(() -> component.prepare(responseBuilder));
    }

    @Test
    void testPrepareWithMapBasedAnalysis() throws IOException {
        ExShieldComponent component = createComponent(
                createRuleConfig("test-rule", "query.count <= 100", null, null)
        );

        // Set analysis as a Map instead of RequestAnalysis
        Map<String, Object> analysisMap = new HashMap<>();
        analysisMap.put("queryAnalysis", Map.of("count", 50));
        requestContext.put("analysis", analysisMap);
        setSolrParams(Map.of());

        assertDoesNotThrow(() -> component.prepare(responseBuilder));
    }

    @Test
    void testGetDescription() {
        ExShieldComponent component = new ExShieldComponent();
        assertNotNull(component.getDescription());
        assertTrue(component.getDescription().contains("ExShield"));
    }

    @Test
    void testCustomBypassParam() throws IOException {
        NamedList<Object> config = new NamedList<>();
        config.add("bypassAllowed", true);
        config.add("bypassParam", "custom.bypass");

        NamedList<Object> rules = new NamedList<>();
        rules.add("rule", createRuleConfig("test-rule", "query.count <= 100", null, null));
        config.add("rules", rules);

        ExShieldComponent component = new ExShieldComponent();
        component.init(config);

        setAnalysis(Map.of("count", 150), null); // Would fail
        setSolrParams(Map.of("custom.bypass", "true"));

        assertDoesNotThrow(() -> component.prepare(responseBuilder));
    }

    private ExShieldComponent createComponent(NamedList<String>... ruleConfigs) {
        NamedList<Object> config = new NamedList<>();
        NamedList<Object> rules = new NamedList<>();
        for (NamedList<String> ruleConfig : ruleConfigs) {
            rules.add("rule", ruleConfig);
        }
        config.add("rules", rules);

        ExShieldComponent component = new ExShieldComponent();
        component.init(config);
        return component;
    }

    private ExShieldComponent createComponentWithBypass(NamedList<String>... ruleConfigs) {
        NamedList<Object> config = new NamedList<>();
        config.add("bypassAllowed", true);

        NamedList<Object> rules = new NamedList<>();
        for (NamedList<String> ruleConfig : ruleConfigs) {
            rules.add("rule", ruleConfig);
        }
        config.add("rules", rules);

        ExShieldComponent component = new ExShieldComponent();
        component.init(config);
        return component;
    }

    private ExShieldComponent createComponentFailOnMissing(NamedList<String>... ruleConfigs) {
        NamedList<Object> config = new NamedList<>();
        config.add("failOnMissingAnalysis", true);

        NamedList<Object> rules = new NamedList<>();
        for (NamedList<String> ruleConfig : ruleConfigs) {
            rules.add("rule", ruleConfig);
        }
        config.add("rules", rules);

        ExShieldComponent component = new ExShieldComponent();
        component.init(config);
        return component;
    }

    private NamedList<String> createRuleConfig(String name, String expression,
                                               String valueExpression, String message) {
        NamedList<String> ruleConfig = new NamedList<>();
        ruleConfig.add("name", name);
        ruleConfig.add("expression", expression);
        if (valueExpression != null) {
            ruleConfig.add("valueExpression", valueExpression);
        }
        if (message != null) {
            ruleConfig.add("message", message);
        }
        return ruleConfig;
    }

    private void setAnalysis(Map<String, Object> queryAnalysis, Map<String, Object> filtersAnalysis) {
        setAnalysis(queryAnalysis, filtersAnalysis, null);
    }

    private void setAnalysis(Map<String, Object> queryAnalysis, Map<String, Object> filtersAnalysis,
                             Map<String, Object> mergedAnalysis) {
        requestContext.put("analysis", new RequestAnalysis() {
            @Override
            public Map<String, Object> queryAnalysis() {
                return queryAnalysis;
            }

            @Override
            public Map<String, Object> filtersAnalysis() {
                return filtersAnalysis;
            }

            @Override
            public Map<String, Object> mergedAnalysis() {
                return mergedAnalysis;
            }
        });
    }

    private void setSolrParams(Map<String, String> params) {
        SolrParams solrParams = new MapSolrParams(params);
        when(solrRequest.getParams()).thenReturn(solrParams);
    }

    // Mock class for testing
    public static class MockStats {
        private final int count;

        public MockStats(int count) {
            this.count = count;
        }

        public int getCount() {
            return count;
        }
    }
}
