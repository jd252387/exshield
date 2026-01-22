package com.exshield.config;

import org.apache.solr.common.util.NamedList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration holder for ExShield component.
 * Parses configuration from solrconfig.xml NamedList.
 * Immutable after construction.
 */
public final class ExShieldConfig {

    public static final String DEFAULT_BYPASS_PARAM = "exshield.bypass";
    public static final int DEFAULT_CACHE_SIZE = 100;
    public static final boolean DEFAULT_BYPASS_ALLOWED = false;
    public static final boolean DEFAULT_FAIL_ON_MISSING_ANALYSIS = false;

    private final List<RuleConfig> rules;
    private final boolean bypassAllowed;
    private final String bypassParam;
    private final int cacheSize;
    private final boolean failOnMissingAnalysis;

    private ExShieldConfig(List<RuleConfig> rules, boolean bypassAllowed, String bypassParam,
                           int cacheSize, boolean failOnMissingAnalysis) {
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        this.bypassAllowed = bypassAllowed;
        this.bypassParam = bypassParam;
        this.cacheSize = cacheSize;
        this.failOnMissingAnalysis = failOnMissingAnalysis;
    }

    /**
     * Parses ExShieldConfig from a NamedList configuration.
     *
     * @param args the NamedList from solrconfig.xml
     * @return the parsed configuration
     */
    @SuppressWarnings("unchecked")
    public static ExShieldConfig fromNamedList(NamedList<?> args) {
        if (args == null) {
            return new ExShieldConfig(
                    Collections.emptyList(),
                    DEFAULT_BYPASS_ALLOWED,
                    DEFAULT_BYPASS_PARAM,
                    DEFAULT_CACHE_SIZE,
                    DEFAULT_FAIL_ON_MISSING_ANALYSIS
            );
        }

        // Parse simple config values
        Boolean bypassAllowed = (Boolean) args.get("bypassAllowed");
        String bypassParam = (String) args.get("bypassParam");
        Integer cacheSize = (Integer) args.get("cacheSize");
        Boolean failOnMissingAnalysis = (Boolean) args.get("failOnMissingAnalysis");

        // Parse rules
        List<RuleConfig> rules = new ArrayList<>();
        NamedList<?> rulesConfig = (NamedList<?>) args.get("rules");
        if (rulesConfig != null) {
            for (int i = 0; i < rulesConfig.size(); i++) {
                String entryName = rulesConfig.getName(i);
                if ("rule".equals(entryName)) {
                    Object value = rulesConfig.getVal(i);
                    if (value instanceof NamedList) {
                        rules.add(RuleConfig.fromNamedList((NamedList<?>) value));
                    }
                }
            }
        }

        return new ExShieldConfig(
                rules,
                bypassAllowed != null ? bypassAllowed : DEFAULT_BYPASS_ALLOWED,
                bypassParam != null && !bypassParam.isBlank() ? bypassParam.trim() : DEFAULT_BYPASS_PARAM,
                cacheSize != null && cacheSize > 0 ? cacheSize : DEFAULT_CACHE_SIZE,
                failOnMissingAnalysis != null ? failOnMissingAnalysis : DEFAULT_FAIL_ON_MISSING_ANALYSIS
        );
    }

    public List<RuleConfig> getRules() {
        return rules;
    }

    public boolean isBypassAllowed() {
        return bypassAllowed;
    }

    public String getBypassParam() {
        return bypassParam;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public boolean isFailOnMissingAnalysis() {
        return failOnMissingAnalysis;
    }

    public boolean hasRules() {
        return !rules.isEmpty();
    }

    @Override
    public String toString() {
        return "ExShieldConfig{" +
                "rules=" + rules.size() +
                ", bypassAllowed=" + bypassAllowed +
                ", bypassParam='" + bypassParam + '\'' +
                ", cacheSize=" + cacheSize +
                ", failOnMissingAnalysis=" + failOnMissingAnalysis +
                '}';
    }
}
