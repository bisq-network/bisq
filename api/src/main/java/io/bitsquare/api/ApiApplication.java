package io.bitsquare.api;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class ApiApplication extends Application<ApiConfiguration> {
    public static void main(String[] args) throws Exception {
        new ApiApplication().run(args);
    }

    @Override
    public String getName() {
        return "hello-world";
    }

    @Override
    public void initialize(Bootstrap<ApiConfiguration> bootstrap) {
        // nothing to do yet
    }

    @Override
    public void run(ApiConfiguration configuration,
                    Environment environment) {
        final ApiResource resource = new ApiResource(
                configuration.getTemplate(),
                configuration.getDefaultName()
        );
        environment.jersey().register(resource);
    }

}
