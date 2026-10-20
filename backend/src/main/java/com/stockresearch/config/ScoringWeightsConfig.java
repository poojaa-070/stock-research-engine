package com.stockresearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds the nested `scoring.category-weights` map from application.yml.
 * (An @Value("#{${scoring.category-weights}}") placeholder can't bind a whole
 * sub-tree, which is why this is @ConfigurationProperties.)
 *
 * v2: ScoringEngine now injects this class directly. v1 exposed the map as a
 * separate Map<String, Double> bean, which only worked because Spring falls
 * back to a Map-typed bean when it finds no beans of the value type — fragile
 * the moment anyone registers a Double bean.
 */
@Configuration
@ConfigurationProperties(prefix = "scoring")
public class ScoringWeightsConfig {

    private Map<String, Double> categoryWeights = new LinkedHashMap<>();

    public Map<String, Double> getCategoryWeights() {
        return categoryWeights;
    }

    public void setCategoryWeights(Map<String, Double> categoryWeights) {
        this.categoryWeights = categoryWeights;
    }
}
