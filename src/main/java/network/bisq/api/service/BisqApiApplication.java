package network.bisq.api.service;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.user.Preferences;
import com.google.inject.Inject;
import com.google.inject.Injector;
import network.bisq.api.BisqProxy;
import network.bisq.api.app.ApiEnvironment;
import network.bisq.api.health.CurrencyListHealthCheck;
import network.bisq.api.service.v1.ApiV1;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.SimpleServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

public class BisqApiApplication extends Application<ApiConfiguration> {

    @Inject
    Injector injector;

    @Inject
    BtcWalletService walletService;

    @Inject
    Preferences preferences;

    private Runnable shutdown;

    @Override
    public String getName() {
        return "Bisq API";
    }

    public void setShutdown(Runnable shutdown) {
        this.shutdown = shutdown;
    }

    @Override
    public void initialize(Bootstrap<ApiConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(new ResourceConfigurationSourceProvider());
        bootstrap.addBundle(new SwaggerBundle<ApiConfiguration>() {
            @Override
            protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(ApiConfiguration configuration) {
                return configuration.swaggerBundleConfiguration;
            }
        });
        // Overriding settings through environment variables, added to override the http port from 8080 to something else
        // See http://www.dropwizard.io/1.1.4/docs/manual/core.html#configuration
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(false)
                )
        );
    }

    @Override
    public void run(ApiConfiguration configuration, Environment environment) {
        BisqProxy bisqProxy = new BisqProxy(injector, shutdown);
        preferences.readPersisted();
        setupCors(environment);
        setupAuth(environment);
        environment.jersey().register(MultiPartFeature.class);
        setupHostAndPort(configuration, injector.getInstance(ApiEnvironment.class));
        final JerseyEnvironment jerseyEnvironment = environment.jersey();
        jerseyEnvironment.register(new ApiV1(bisqProxy));
        ExceptionMappers.register(jerseyEnvironment);
        environment.healthChecks().register("currency list size", new CurrencyListHealthCheck(bisqProxy));
    }

    private void setupAuth(Environment environment) {
        final FilterRegistration.Dynamic auth = environment.servlets().addFilter("Auth", new AuthFilter(walletService, injector.getInstance(TokenRegistry.class)));
        auth.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    }

    private void setupHostAndPort(ApiConfiguration configuration, ApiEnvironment environment) {
        final SimpleServerFactory serverFactory = (SimpleServerFactory) configuration.getServerFactory();
        final HttpConnectorFactory connector = (HttpConnectorFactory) serverFactory.getConnector();
        final Integer apiPort = environment.getApiPort();
        if (null != apiPort)
            connector.setPort(apiPort);
        final String apiHost = environment.getApiHost();
        if (null != apiHost)
            connector.setBindHost(apiHost);
    }

    private void setupCors(Environment environment) {
        final FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);

        // Configure CORS parameters
        cors.setInitParameter("allowedOrigins", "*");
        cors.setInitParameter("allowedHeaders", "*");
        cors.setInitParameter("allowedMethods", "OPTIONS,GET,PUT,POST,DELETE,HEAD");

        // Add URL mapping
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    }

}
