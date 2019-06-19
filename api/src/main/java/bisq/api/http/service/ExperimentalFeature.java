package bisq.api.http.service;

import bisq.api.http.exceptions.ExperimentalFeatureException;

import bisq.core.app.AppOptionKeys;
import bisq.core.app.BisqEnvironment;

import javax.inject.Inject;

public class ExperimentalFeature {

    public static final String NOTE = "This is EXPERIMENTAL FEATURE! Run it at your own risk! Requires --" + AppOptionKeys.HTTP_API_EXPERIMENTAL_FEATURES_ENABLED + " flag at startup.";
    private final BisqEnvironment environment;

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
