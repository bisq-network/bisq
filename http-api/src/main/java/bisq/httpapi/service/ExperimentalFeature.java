package bisq.httpapi.service;

import bisq.core.app.AppOptionKeys;
import bisq.core.app.BisqEnvironment;

import bisq.httpapi.exceptions.ExperimentalFeatureException;

import javax.inject.Inject;

public class ExperimentalFeature {

    private final BisqEnvironment environment;

    public static final String NOTE = "This is EXPERIMENTAL FEATURE! Run it at your own risk! Requires --" + AppOptionKeys.HTTP_API_EXPERIMENTAL_FEATURES_ENABLED + " flag at startup.";

    @Inject
    public ExperimentalFeature(BisqEnvironment environment) {
        this.environment = environment;
    }

    public void assertEnabled() {
        if (!environment.isHttpApiExperimentalFeaturesEnabled()) {
            throw new ExperimentalFeatureException();
        }
    }
}
