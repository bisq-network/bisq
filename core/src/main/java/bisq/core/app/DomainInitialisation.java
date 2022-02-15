/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.app;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.alert.PrivateNotificationPayload;
import bisq.core.btc.Balances;
import bisq.core.dao.DaoSetup;
import bisq.core.dao.governance.voteresult.VoteResultException;
import bisq.core.dao.governance.voteresult.VoteResultService;
import bisq.core.dao.state.DaoStateSnapshotService;
import bisq.core.filter.FilterManager;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.notifications.alerts.DisputeMsgEvents;
import bisq.core.notifications.alerts.MyOfferTakenEvents;
import bisq.core.notifications.alerts.TradeEvents;
import bisq.core.notifications.alerts.market.MarketAlerts;
import bisq.core.notifications.alerts.price.PriceAlert;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bisq_v1.TriggerPriceService;
import bisq.core.offer.bsq_swap.OpenBsqSwapOfferService;
import bisq.core.payment.AmazonGiftCardAccount;
import bisq.core.payment.RevolutAccount;
import bisq.core.payment.TradeLimits;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;
import bisq.core.support.traderchat.TraderChatManager;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.trade.txproof.xmr.XmrTxProofService;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;

import bisq.common.ClockWatcher;
import bisq.common.app.DevEnv;
import bisq.common.persistence.PersistenceManager;

import javax.inject.Inject;

import javafx.collections.ListChangeListener;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Handles the initialisation of domain classes. We should refactor to the model that the domain classes listen on the
 * relevant start up state from AppStartupState instead to get called. Only for initialisation which has a required
 * order we will still need this class. For now it helps to keep BisqSetup more focussed on the process and not getting
 * overloaded with domain initialisation code.
 */
public class DomainInitialisation {
    private final ClockWatcher clockWatcher;
    private final TradeLimits tradeLimits;
    private final ArbitrationManager arbitrationManager;
    private final MediationManager mediationManager;
    private final RefundManager refundManager;
    private final TraderChatManager traderChatManager;
    private final TradeManager tradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final BsqSwapTradeManager bsqSwapTradeManager;
    private final FailedTradesManager failedTradesManager;
    private final XmrTxProofService xmrTxProofService;
    private final OpenOfferManager openOfferManager;
    private final Balances balances;
    private final WalletAppSetup walletAppSetup;
    private final ArbitratorManager arbitratorManager;
    private final MediatorManager mediatorManager;
    private final RefundAgentManager refundAgentManager;
    private final PrivateNotificationManager privateNotificationManager;
    private final P2PService p2PService;
    private final FeeService feeService;
    private final DaoSetup daoSetup;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final SignedWitnessService signedWitnessService;
    private final PriceFeedService priceFeedService;
    private final FilterManager filterManager;
    private final VoteResultService voteResultService;
    private final MobileNotificationService mobileNotificationService;
    private final MyOfferTakenEvents myOfferTakenEvents;
    private final TradeEvents tradeEvents;
    private final DisputeMsgEvents disputeMsgEvents;
    private final PriceAlert priceAlert;
    private final MarketAlerts marketAlerts;
    private final User user;
    private final DaoStateSnapshotService daoStateSnapshotService;
    private final TriggerPriceService triggerPriceService;
    private final MempoolService mempoolService;
    private final OpenBsqSwapOfferService openBsqSwapOfferService;

    @Inject
    public DomainInitialisation(ClockWatcher clockWatcher,
                                TradeLimits tradeLimits,
                                ArbitrationManager arbitrationManager,
                                MediationManager mediationManager,
                                RefundManager refundManager,
                                TraderChatManager traderChatManager,
                                TradeManager tradeManager,
                                ClosedTradableManager closedTradableManager,
                                BsqSwapTradeManager bsqSwapTradeManager,
                                FailedTradesManager failedTradesManager,
                                XmrTxProofService xmrTxProofService,
                                OpenOfferManager openOfferManager,
                                Balances balances,
                                WalletAppSetup walletAppSetup,
                                ArbitratorManager arbitratorManager,
                                MediatorManager mediatorManager,
                                RefundAgentManager refundAgentManager,
                                PrivateNotificationManager privateNotificationManager,
                                P2PService p2PService,
                                FeeService feeService,
                                DaoSetup daoSetup,
                                TradeStatisticsManager tradeStatisticsManager,
                                AccountAgeWitnessService accountAgeWitnessService,
                                SignedWitnessService signedWitnessService,
                                PriceFeedService priceFeedService,
                                FilterManager filterManager,
                                VoteResultService voteResultService,
                                MobileNotificationService mobileNotificationService,
                                MyOfferTakenEvents myOfferTakenEvents,
                                TradeEvents tradeEvents,
                                DisputeMsgEvents disputeMsgEvents,
                                PriceAlert priceAlert,
                                MarketAlerts marketAlerts,
                                User user,
                                DaoStateSnapshotService daoStateSnapshotService,
                                TriggerPriceService triggerPriceService,
                                MempoolService mempoolService,
                                OpenBsqSwapOfferService openBsqSwapOfferService) {
        this.clockWatcher = clockWatcher;
        this.tradeLimits = tradeLimits;
        this.arbitrationManager = arbitrationManager;
        this.mediationManager = mediationManager;
        this.refundManager = refundManager;
        this.traderChatManager = traderChatManager;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.bsqSwapTradeManager = bsqSwapTradeManager;
        this.failedTradesManager = failedTradesManager;
        this.xmrTxProofService = xmrTxProofService;
        this.openOfferManager = openOfferManager;
        this.balances = balances;
        this.walletAppSetup = walletAppSetup;
        this.arbitratorManager = arbitratorManager;
        this.mediatorManager = mediatorManager;
        this.refundAgentManager = refundAgentManager;
        this.privateNotificationManager = privateNotificationManager;
        this.p2PService = p2PService;
        this.feeService = feeService;
        this.daoSetup = daoSetup;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.signedWitnessService = signedWitnessService;
        this.priceFeedService = priceFeedService;
        this.filterManager = filterManager;
        this.voteResultService = voteResultService;
        this.mobileNotificationService = mobileNotificationService;
        this.myOfferTakenEvents = myOfferTakenEvents;
        this.tradeEvents = tradeEvents;
        this.disputeMsgEvents = disputeMsgEvents;
        this.priceAlert = priceAlert;
        this.marketAlerts = marketAlerts;
        this.user = user;
        this.daoStateSnapshotService = daoStateSnapshotService;
        this.triggerPriceService = triggerPriceService;
        this.mempoolService = mempoolService;
        this.openBsqSwapOfferService = openBsqSwapOfferService;
    }

    public void initDomainServices(Consumer<String> rejectedTxErrorMessageHandler,
                                   Consumer<PrivateNotificationPayload> displayPrivateNotificationHandler,
                                   Consumer<String> daoErrorMessageHandler,
                                   Consumer<String> daoWarnMessageHandler,
                                   Consumer<String> filterWarningHandler,
                                   Consumer<VoteResultException> voteResultExceptionHandler,
                                   Consumer<List<RevolutAccount>> revolutAccountsUpdateHandler,
                                   Consumer<List<AmazonGiftCardAccount>> amazonGiftCardAccountsUpdateHandler,
                                   Runnable daoRequiresRestartHandler) {
        clockWatcher.start();

        PersistenceManager.onAllServicesInitialized();

        tradeLimits.onAllServicesInitialized();

        tradeManager.onAllServicesInitialized();
        arbitrationManager.onAllServicesInitialized();
        mediationManager.onAllServicesInitialized();
        refundManager.onAllServicesInitialized();
        traderChatManager.onAllServicesInitialized();

        closedTradableManager.onAllServicesInitialized();
        bsqSwapTradeManager.onAllServicesInitialized();
        failedTradesManager.onAllServicesInitialized();
        xmrTxProofService.onAllServicesInitialized();

        openOfferManager.onAllServicesInitialized();
        openBsqSwapOfferService.onAllServicesInitialized();

        balances.onAllServicesInitialized();

        walletAppSetup.setRejectedTxErrorMessageHandler(rejectedTxErrorMessageHandler, openOfferManager, tradeManager);

        arbitratorManager.onAllServicesInitialized();
        mediatorManager.onAllServicesInitialized();
        refundAgentManager.onAllServicesInitialized();

        privateNotificationManager.privateNotificationProperty().addListener((observable, oldValue, newValue) -> {
            if (displayPrivateNotificationHandler != null)
                displayPrivateNotificationHandler.accept(newValue);
        });

        p2PService.onAllServicesInitialized();

        feeService.onAllServicesInitialized(filterManager);

        if (DevEnv.isDaoActivated()) {
            daoSetup.onAllServicesInitialized(errorMessage -> {
                if (daoErrorMessageHandler != null)
                    daoErrorMessageHandler.accept(errorMessage);
            }, warningMessage -> {
                if (daoWarnMessageHandler != null)
                    daoWarnMessageHandler.accept(warningMessage);
            });

            daoStateSnapshotService.setDaoRequiresRestartHandler(daoRequiresRestartHandler);
        }

        tradeStatisticsManager.onAllServicesInitialized();

        accountAgeWitnessService.onAllServicesInitialized();
        signedWitnessService.onAllServicesInitialized();

        priceFeedService.setCurrencyCodeOnInit();

        filterManager.setFilterWarningHandler(filterWarningHandler);
        filterManager.onAllServicesInitialized();

        voteResultService.getVoteResultExceptions().addListener((ListChangeListener<VoteResultException>) c -> {
            c.next();
            if (c.wasAdded() && voteResultExceptionHandler != null) {
                c.getAddedSubList().forEach(voteResultExceptionHandler);
            }
        });

        mobileNotificationService.onAllServicesInitialized();
        myOfferTakenEvents.onAllServicesInitialized();
        tradeEvents.onAllServicesInitialized();
        disputeMsgEvents.onAllServicesInitialized();
        priceAlert.onAllServicesInitialized();
        marketAlerts.onAllServicesInitialized();
        triggerPriceService.onAllServicesInitialized();
        mempoolService.onAllServicesInitialized();

        if (revolutAccountsUpdateHandler != null) {
            revolutAccountsUpdateHandler.accept(user.getPaymentAccountsAsObservable().stream()
                    .filter(paymentAccount -> paymentAccount instanceof RevolutAccount)
                    .map(paymentAccount -> (RevolutAccount) paymentAccount)
                    .filter(RevolutAccount::userNameNotSet)
                    .collect(Collectors.toList()));
        }
        if (amazonGiftCardAccountsUpdateHandler != null) {
            amazonGiftCardAccountsUpdateHandler.accept(user.getPaymentAccountsAsObservable().stream()
                    .filter(paymentAccount -> paymentAccount instanceof AmazonGiftCardAccount)
                    .map(paymentAccount -> (AmazonGiftCardAccount) paymentAccount)
                    .filter(AmazonGiftCardAccount::countryNotSet)
                    .collect(Collectors.toList()));
        }
    }
}
