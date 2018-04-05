package io.bisq.api.service;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import io.bisq.api.BisqProxy;
import io.bisq.api.health.CurrencyListHealthCheck;
import io.bisq.api.service.v1.ApiV1;
import bisq.common.crypto.KeyRing;
import bisq.common.storage.Storage;
import bisq.core.app.AppOptionKeys;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.TradeManager;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.network.p2p.P2PService;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import org.eclipse.jetty.servlets.CrossOriginFilter;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

public class BisqApiApplication extends Application<ApiConfiguration> {

    @Inject
    Injector injector;

    @Inject
    AccountAgeWitnessService accountAgeWitnessService;

    @Inject
    ArbitratorManager arbitratorManager;

    @Inject
    BtcWalletService walletService;

    @Inject
    WalletsSetup walletsSetup;

    @Inject
    TradeManager tradeManager;

    @Inject
    ClosedTradableManager closedTradableManager;

    @Inject
    FailedTradesManager failedTradesManager;

    @Inject
    OpenOfferManager openOfferManager;

    @Inject
    OfferBookService offerBookService;

    @Inject
    P2PService p2PService;

    @Inject
    KeyRing keyRing;

    @Inject
    User user;

    @Inject
    FeeService feeService;

    @Inject
    Storage storage;

    @Inject
    private Preferences preferences;

    @Inject
    private BsqWalletService bsqWalletService;

    @Inject(optional = true)
    @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS)
    private boolean useDevPrivilegeKeys = false;

    public static void main(String[] args) throws Exception {
        new BisqApiApplication().run(args);
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
        BisqProxy bisqProxy = new BisqProxy(injector, accountAgeWitnessService, arbitratorManager, walletService, tradeManager, openOfferManager,
                offerBookService, p2PService, keyRing, user, feeService, preferences, bsqWalletService,
                walletsSetup, closedTradableManager, failedTradesManager, useDevPrivilegeKeys);
        preferences.readPersisted();
        setupCors(environment);
        final JerseyEnvironment jerseyEnvironment = environment.jersey();
        jerseyEnvironment.register(new ApiV1(bisqProxy));
        ExceptionMappers.register(jerseyEnvironment);
        environment.healthChecks().register("currency list size", new CurrencyListHealthCheck(bisqProxy));
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
