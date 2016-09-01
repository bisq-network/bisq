package io.bitsquare.api.app;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Inject;
import io.bitsquare.api.ApiConfiguration;
import io.bitsquare.api.ApiResource;
import io.bitsquare.trade.statistics.TradeStatisticsManager;
import io.dropwizard.Application;
import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class DropwizardApplication extends Application<ApiConfiguration> {
    @Inject
    TradeStatisticsManager tradeStatisticsManager;

    public static void main(String[] args) throws Exception {
        new DropwizardApplication().run(args);
    }

    @Override
    public String getName() {
        return "Bitsquare API";
    }

    @Override
    public void initialize(Bootstrap<ApiConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(
                new ResourceConfigurationSourceProvider());
    }

    @Override
    public void run(ApiConfiguration configuration,
                    Environment environment) {
//        environment.getObjectMapper().configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        final ApiResource resource = new ApiResource(
                configuration.getTemplate(),
                configuration.getDefaultName(),
                tradeStatisticsManager
        );
        environment.jersey().register(resource);
    }

}
