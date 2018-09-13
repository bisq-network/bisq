package bisq.httpapi.service;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.BtcWalletService;

import bisq.httpapi.exceptions.ExceptionMappers;
import bisq.httpapi.service.auth.AuthFilter;
import bisq.httpapi.service.auth.TokenRegistry;
import bisq.httpapi.util.CurrencyListHealthCheck;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;

import javax.inject.Inject;

import java.util.EnumSet;



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

public class HttpApiServer extends Application<HttpApiConfiguration> {
    private final BtcWalletService walletService;
    private final HttpApiInterfaceV1 httpApiInterfaceV1;
    private final TokenRegistry tokenRegistry;
    private final BisqEnvironment bisqEnvironment;

    @Inject
    public HttpApiServer(BtcWalletService walletService, HttpApiInterfaceV1 httpApiInterfaceV1,
                         TokenRegistry tokenRegistry, BisqEnvironment bisqEnvironment) {
        this.walletService = walletService;
        this.httpApiInterfaceV1 = httpApiInterfaceV1;
        this.tokenRegistry = tokenRegistry;
        this.bisqEnvironment = bisqEnvironment;
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
    public void initialize(Bootstrap<HttpApiConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(new ResourceConfigurationSourceProvider());
        bootstrap.addBundle(new SwaggerBundle<HttpApiConfiguration>() {
            @Override
            protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(HttpApiConfiguration configuration) {
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
    public void run(HttpApiConfiguration configuration, Environment environment) {
        setupCrossOriginFilter(environment);
        setupAuth(environment);
        environment.jersey().register(MultiPartFeature.class);
        setupHostAndPort(configuration);
        JerseyEnvironment jerseyEnvironment = environment.jersey();
        jerseyEnvironment.register(httpApiInterfaceV1);
        ExceptionMappers.register(jerseyEnvironment);
        environment.healthChecks().register("currency list size", new CurrencyListHealthCheck());
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

    private void setupAuth(Environment environment) {
        AuthFilter authFilter = new AuthFilter(walletService, tokenRegistry);
        final FilterRegistration.Dynamic auth = environment.servlets().addFilter("Auth", authFilter);
        auth.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    }

    private void setupHostAndPort(HttpApiConfiguration configuration) {
        SimpleServerFactory serverFactory = (SimpleServerFactory) configuration.getServerFactory();
        HttpConnectorFactory connector = (HttpConnectorFactory) serverFactory.getConnector();
        connector.setPort(Integer.valueOf(bisqEnvironment.getHttpApiPort()));
        connector.setBindHost(bisqEnvironment.getHttpApiHost());
    }
}
