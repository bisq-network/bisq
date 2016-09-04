package io.bitsquare.api.service;

import com.google.inject.Inject;
import io.bitsquare.api.BitsquareProxy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.user.User;
import io.dropwizard.Application;
import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class DropwizardApplication extends Application<ApiConfiguration> {
    @Inject
    WalletService walletService;

    @Inject
    User user;

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
        BitsquareProxy bitsquareProxy = new BitsquareProxy(walletService, user);
        final ApiResource resource = new ApiResource(
                configuration.getTemplate(),
                configuration.getDefaultName(),
                bitsquareProxy
        );
        environment.jersey().register(resource);
    }

}
