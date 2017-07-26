package io.bisq.api.service;

import com.google.inject.Inject;
import io.bisq.api.BisqProxy;
import io.bisq.api.health.CurrencyListHealthCheck;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.OfferBookService;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.network.p2p.P2PService;
import io.dropwizard.Application;
import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;

public class DropwizardApplication extends Application<ApiConfiguration> {
    @Inject
    BtcWalletService walletService;

    @Inject
    TradeManager tradeManager;

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

    public static void main(String[] args) throws Exception {
        new DropwizardApplication().run(args);
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
    }

    @Override
    public void run(ApiConfiguration configuration,
                    Environment environment) {
//        environment.getObjectMapper().configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        BisqProxy bisqProxy = new BisqProxy(walletService, tradeManager, openOfferManager,
                offerBookService, p2PService, keyRing, priceFeedService, user, feeService, preferences, bsqWalletService);
        final ApiResourceV1 resource = new ApiResourceV1(
                configuration.getTemplate(),
                configuration.getDefaultName(),
                bisqProxy
        );
        preferences.readPersisted();
        environment.jersey().register(resource);
        environment.healthChecks().register("currency list size", new CurrencyListHealthCheck(bisqProxy));
    }

}
