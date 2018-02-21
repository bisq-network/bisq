package io.bisq.api.service;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bisq.api.BisqProxy;
import io.bisq.api.health.CurrencyListHealthCheck;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.storage.Storage;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.arbitration.ArbitratorManager;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.WalletsSetup;
import io.bisq.core.offer.OfferBookService;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.core.trade.failed.FailedTradesManager;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.network.p2p.P2PService;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;

public class BisqApiApplication extends Application<ApiConfiguration> {
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
    PriceFeedService priceFeedService;

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
        bootstrap.setConfigurationSourceProvider(
                new ResourceConfigurationSourceProvider());
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
    public void run(ApiConfiguration configuration,
                    Environment environment) {
//        environment.getObjectMapper().configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        BisqProxy bisqProxy = new BisqProxy(arbitratorManager, walletService, tradeManager, openOfferManager,
                offerBookService, p2PService, keyRing, priceFeedService, user, feeService, preferences, bsqWalletService,
                walletsSetup, closedTradableManager, failedTradesManager, useDevPrivilegeKeys);
        final ApiResourceV1 resource = new ApiResourceV1(configuration.getDefaultName(), bisqProxy);
        preferences.readPersisted();
        environment.jersey().register(resource);
        environment.healthChecks().register("currency list size", new CurrencyListHealthCheck(bisqProxy));
    }

}
