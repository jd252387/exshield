package com.exshield;

import com.exshield.config.ExShieldConfig;
import com.exshield.config.RuleConfig;
import com.exshield.jexl.EvaluationResult;
import com.exshield.jexl.JexlEvaluator;
import com.exshield.jexl.JexlEvaluator.JexlEvaluationException;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Map;

/**
 * Solr SearchComponent that blocks heavy queries based on JEXL expressions
 * evaluated against RequestAnalysis data from a query analyzer component.
 *
 * <p>Configuration example in solrconfig.xml:</p>
 * <pre>{@code
 * <searchComponent name="exshield" class="com.exshield.ExShieldComponent">
 *   <bool name="bypassAllowed">true</bool>
 *   <str name="bypassParam">exshield.bypass</str>
 *   <int name="cacheSize">100</int>
 *   <bool name="failOnMissingAnalysis">false</bool>
 *
 *   <lst name="rules">
 *     <lst name="rule">
 *       <str name="name">max-term-count</str>
 *       <str name="expression">clause_statistics.getTermCount() <= 1000</str>
 *       <str name="valueExpression">clause_statistics.getTermCount()</str>
 *       <str name="message">Query has too many terms. Maximum allowed is 1000.</str>
 *     </lst>
 *   </lst>
 * </searchComponent>
 * }</pre>
 */
public class ExShieldComponent extends SearchComponent {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String ANALYSIS_CONTEXT_KEY = "analysis";
    public static final String COMPONENT_NAME = "exshield";

    private ExShieldConfig config;
    private JexlEvaluator evaluator;

    @Override
    public void init(NamedList<?> args) {
        super.init(args);
        this.config = ExShieldConfig.fromNamedList(args);
        this.evaluator = new JexlEvaluator(config.getCacheSize());
        log.info("ExShield initialized with {} rules, bypassAllowed={}, bypassParam={}",
                config.getRules().size(), config.isBypassAllowed(), config.getBypassParam());
    }

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        // Check for bypass
        if (isBypassRequested(rb)) {
            log.debug("ExShield bypassed for request");
            return;
        }

        // Skip if no rules configured
        if (!config.hasRules()) {
            log.debug("ExShield has no rules configured, skipping");
            return;
        }

        // Get analysis from request context
        RequestAnalysis analysis = getAnalysis(rb);
        if (analysis == null) {
            handleMissingAnalysis(rb);
            return;
        }

        // Evaluate all rules
        Map<String, Object> queryAnalysis = analysis.queryAnalysis();
        Map<String, Object> filtersAnalysis = analysis.filtersAnalysis();
        Map<String, Object> mergedAnalysis = analysis.mergedAnalysis();

        for (RuleConfig rule : config.getRules()) {
            evaluateRule(rule, queryAnalysis, filtersAnalysis, mergedAnalysis);
        }

        log.debug("ExShield passed all {} rules", config.getRules().size());
    }

    @Override
    public void process(ResponseBuilder rb) throws IOException {
        // No processing needed - blocking happens in prepare()
    }

    @Override
    public String getDescription() {
        return "ExShield - Blocks heavy queries based on JEXL expressions evaluated against query analysis";
    }

    /**
     * Checks if bypass is requested and allowed.
     */
    private boolean isBypassRequested(ResponseBuilder rb) {
        if (!config.isBypassAllowed()) {
            return false;
        }
        SolrParams params = rb.req.getParams();
        return params.getBool(config.getBypassParam(), false);
    }

    /**
     * Retrieves RequestAnalysis from the request context.
     */
    @SuppressWarnings("unchecked")
    private RequestAnalysis getAnalysis(ResponseBuilder rb) {
        Object analysisObj = rb.req.getContext().get(ANALYSIS_CONTEXT_KEY);
        if (analysisObj == null) {
            return null;
        }
        if (analysisObj instanceof RequestAnalysis) {
            return (RequestAnalysis) analysisObj;
        }
        // Try to adapt if it's a Map-like structure
        if (analysisObj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) analysisObj;
            return new RequestAnalysis() {
                @Override
                @SuppressWarnings("unchecked")
                public Map<String, Object> queryAnalysis() {
                    Object qa = map.get("queryAnalysis");
                    return qa instanceof Map ? (Map<String, Object>) qa : null;
                }

                @Override
                @SuppressWarnings("unchecked")
                public Map<String, Object> filtersAnalysis() {
                    Object fa = map.get("filtersAnalysis");
                    return fa instanceof Map ? (Map<String, Object>) fa : null;
                }

                @Override
                @SuppressWarnings("unchecked")
                public Map<String, Object> mergedAnalysis() {
                    Object ma = map.get("mergedAnalysis");
                    return ma instanceof Map ? (Map<String, Object>) ma : null;
                }
            };
        }
        log.warn("Unknown analysis type in context: {}", analysisObj.getClass().getName());
        return null;
    }

    /**
     * Handles missing analysis based on configuration.
     */
    private void handleMissingAnalysis(ResponseBuilder rb) {
        if (config.isFailOnMissingAnalysis()) {
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                    "ExShield: RequestAnalysis not found in request context. " +
                            "Ensure a query analyzer component runs before ExShield.");
        }
        log.warn("ExShield: RequestAnalysis not found in request context, skipping rule evaluation");
    }

    /**
     * Evaluates a single rule and throws SolrException if it fails.
     */
    private void evaluateRule(RuleConfig rule, Map<String, Object> queryAnalysis,
                              Map<String, Object> filtersAnalysis, Map<String, Object> mergedAnalysis) {
        try {
            EvaluationResult result = evaluator.evaluate(rule, queryAnalysis, filtersAnalysis, mergedAnalysis);
            if (!result.passed()) {
                String errorMessage = buildErrorMessage(rule, result);
                log.info("ExShield blocked request: {}", errorMessage);
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, errorMessage);
            }
        } catch (JexlEvaluationException e) {
            String errorMessage = "ExShield rule '" + rule.getName() +
                    "' evaluation failed: " + e.getMessage();
            log.error(errorMessage, e);
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, errorMessage, e);
        }
    }

    /**
     * Builds the error message for a failed rule.
     */
    private String buildErrorMessage(RuleConfig rule, EvaluationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Query blocked by ExShield rule '").append(rule.getName()).append("': ");
        sb.append("expression '").append(rule.getExpression()).append("' evaluated to false.");

        if (result.actualValue() != null) {
            sb.append(" Actual value: ").append(result.actualValue());
        }

        if (rule.hasMessage()) {
            sb.append(" (").append(rule.getMessage()).append(")");
        }

        return sb.toString();
    }

    /**
     * Interface for RequestAnalysis data.
     * This interface is expected to be provided by a query analyzer component.
     */
    public interface RequestAnalysis {
        /**
         * Returns the query analysis data as a map.
         * Exposed as 'query' in JEXL expressions.
         */
        Map<String, Object> queryAnalysis();

        /**
         * Returns the filter analysis data as a map.
         * Exposed as 'filters' in JEXL expressions.
         */
        Map<String, Object> filtersAnalysis();

        /**
         * Returns the merged analysis data as a map.
         * This is the combined view of query and filter analysis from Solr context.
         * Exposed as 'total' in JEXL expressions.
         */
        Map<String, Object> mergedAnalysis();
    }
}
