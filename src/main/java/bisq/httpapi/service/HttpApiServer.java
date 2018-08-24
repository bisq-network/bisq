package bisq.httpapi.service;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.user.Preferences;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;

import com.google.inject.Injector;

import javax.inject.Inject;

import java.util.EnumSet;

import lombok.Setter;



import bisq.httpapi.BisqProxy;
import bisq.httpapi.health.CurrencyListHealthCheck;
import bisq.httpapi.service.auth.AuthFilter;
import bisq.httpapi.service.auth.TokenRegistry;
import bisq.httpapi.service.v1.ApiV1;
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

public class HttpApiServer extends Application<ApiConfiguration> {
    private final Injector injector;
    private final BtcWalletService walletService;
    private final Preferences preferences;
    private final Runnable shutdownHandler;
    @Setter
    private Runnable hostShutdownHandler;

    @Inject
    public HttpApiServer(Injector injector, BtcWalletService walletService, Preferences preferences) {
        this.injector = injector;
        this.walletService = walletService;
        this.preferences = preferences;
        shutdownHandler = () -> {
            // TODO add here API specific shut down procedure
            if (hostShutdownHandler != null)
                hostShutdownHandler.run();
        };
    }

    public void startServer() {
        try {
            HttpApiServer.this.run("server", "bisq-api.yml");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return "Bisq API";
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
        BisqProxy bisqProxy = new BisqProxy(injector, shutdownHandler);
        //preferences.readPersisted();
        setupCrossOriginFilter(environment);
        setupAuth(environment);
        environment.jersey().register(MultiPartFeature.class);
        setupHostAndPort(configuration, injector.getInstance(BisqEnvironment.class));
        JerseyEnvironment jerseyEnvironment = environment.jersey();
        jerseyEnvironment.register(new ApiV1(bisqProxy));
        ExceptionMappers.register(jerseyEnvironment);
        environment.healthChecks().register("currency list size", new CurrencyListHealthCheck(bisqProxy));
    }

    private void setupAuth(Environment environment) {
        final FilterRegistration.Dynamic auth = environment.servlets().addFilter("Auth", new AuthFilter(walletService, injector.getInstance(TokenRegistry.class)));
        auth.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    }

    private void setupHostAndPort(ApiConfiguration configuration, BisqEnvironment environment) {
        SimpleServerFactory serverFactory = (SimpleServerFactory) configuration.getServerFactory();
        HttpConnectorFactory connector = (HttpConnectorFactory) serverFactory.getConnector();
        connector.setPort(Integer.valueOf(environment.getHttpApiPort()));
        connector.setBindHost(environment.getHttpApiHost());
    }

    private void setupCrossOriginFilter(Environment environment) {
        final FilterRegistration.Dynamic crossOriginFilter = environment.servlets().addFilter("CORS", CrossOriginFilter.class);

        // Configure CrossOriginFilter parameters
        crossOriginFilter.setInitParameter("allowedOrigins", "*");
        crossOriginFilter.setInitParameter("allowedHeaders", "*");
        crossOriginFilter.setInitParameter("allowedMethods", "OPTIONS,GET,PUT,POST,DELETE,HEAD");

        // Add URL mapping
        crossOriginFilter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    }

}
