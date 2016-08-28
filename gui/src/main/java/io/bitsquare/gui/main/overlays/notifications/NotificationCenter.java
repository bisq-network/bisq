package io.bitsquare.gui.main.overlays.notifications;

import com.google.inject.Inject;
import io.bitsquare.app.Log;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.disputes.DisputesView;
import io.bitsquare.gui.main.disputes.trader.TraderDisputeView;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesView;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.Preferences;
import javafx.collections.ListChangeListener;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class NotificationCenter {
    private static final Logger log = LoggerFactory.getLogger(NotificationCenter.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final static List<Notification> notifications = new ArrayList<>();
    private Consumer<String> selectItemByTradeIdConsumer;

    static void add(Notification notification) {
        notifications.add(notification);
    }

    static boolean useAnimations;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final TradeManager tradeManager;
    private final DisputeManager disputeManager;
    private Preferences preferences;
    private final Navigation navigation;

    private final Map<String, Subscription> disputeStateSubscriptionsMap = new HashMap<>();
    private final Map<String, Subscription> tradeStateSubscriptionsMap = new HashMap<>();
    @Nullable
    private String selectedTradeId;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public NotificationCenter(TradeManager tradeManager, DisputeManager disputeManager, Preferences preferences, Navigation navigation) {
        this.tradeManager = tradeManager;
        this.disputeManager = disputeManager;
        this.preferences = preferences;
        this.navigation = navigation;

        EasyBind.subscribe(preferences.useAnimationsProperty(), useAnimations -> NotificationCenter.useAnimations = useAnimations);
    }

    public void onAllServicesAndViewsInitialized() {
        tradeManager.getTrades().addListener((ListChangeListener<Trade>) change -> {
            change.next();
            if (change.wasRemoved()) {
                change.getRemoved().stream().forEach(trade -> {
                    String tradeId = trade.getId();
                    if (disputeStateSubscriptionsMap.containsKey(tradeId)) {
                        disputeStateSubscriptionsMap.get(tradeId).unsubscribe();
                        disputeStateSubscriptionsMap.remove(tradeId);
                    }

                    if (tradeStateSubscriptionsMap.containsKey(tradeId)) {
                        tradeStateSubscriptionsMap.get(tradeId).unsubscribe();
                        tradeStateSubscriptionsMap.remove(tradeId);
                    }
                });
            }
            if (change.wasAdded()) {
                change.getAddedSubList().stream().forEach(trade -> {
                    String tradeId = trade.getId();
                    if (disputeStateSubscriptionsMap.containsKey(tradeId)) {
                        log.debug("We have already an entry in disputeStateSubscriptionsMap.");
                    } else {
                        Subscription disputeStateSubscription = EasyBind.subscribe(trade.disputeStateProperty(), disputeState -> onDisputeStateChanged(trade, disputeState));
                        disputeStateSubscriptionsMap.put(tradeId, disputeStateSubscription);
                    }

                    if (tradeStateSubscriptionsMap.containsKey(tradeId)) {
                        log.debug("We have already an entry in tradeStateSubscriptionsMap.");
                    } else {
                        Subscription tradeStateSubscription = EasyBind.subscribe(trade.stateProperty(), tradeState -> onTradeStateChanged(trade, tradeState));
                        tradeStateSubscriptionsMap.put(tradeId, tradeStateSubscription);
                    }
                });
            }
        });

        tradeManager.getTrades().stream()
                .forEach(trade -> {
                    String tradeId = trade.getId();
                    Subscription disputeStateSubscription = EasyBind.subscribe(trade.disputeStateProperty(), disputeState -> onDisputeStateChanged(trade, disputeState));
                    disputeStateSubscriptionsMap.put(tradeId, disputeStateSubscription);

                    Subscription tradeStateSubscription = EasyBind.subscribe(trade.stateProperty(), tradeState -> onTradeStateChanged(trade, tradeState));
                    tradeStateSubscriptionsMap.put(tradeId, tradeStateSubscription);
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter/Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    @org.jetbrains.annotations.Nullable
    public String getSelectedTradeId() {
        return selectedTradeId;
    }

    public void setSelectedTradeId(@Nullable String selectedTradeId) {
        this.selectedTradeId = selectedTradeId;
    }

    public void setSelectItemByTradeIdConsumer(Consumer<String> selectItemByTradeIdConsumer) {
        this.selectItemByTradeIdConsumer = selectItemByTradeIdConsumer;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onTradeStateChanged(Trade trade, Trade.State tradeState) {
        Log.traceCall(tradeState.toString());
        String message = null;

        if (tradeState == Trade.State.PAYOUT_BROAD_CASTED) {
            message = "The trade is now completed and you can withdraw your funds.";
        } else {
            if (tradeManager.isBuyer(trade.getOffer())) {
                switch (tradeState) {
                    case OFFERER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG:
                        message = "Your offer has been accepted by a BTC seller.";
                        break;
                    case DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN:
                        message = "Your trade has at least one blockchain confirmation.\n" +
                                "You can start the payment now.";
                        break;
                }
            } else {
                switch (tradeState) {
                    case OFFERER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG:
                        message = "Your offer has been accepted by a BTC buyer.";
                        break;
                    case SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG:
                        message = "The BTC buyer has started the payment.";
                        break;
                }
            }
        }

        if (message != null) {
            String key = tradeState.name() + trade.getId();
            if (preferences.showAgain(key)) {
                Notification notification = new Notification().tradeHeadLine(trade.getShortId()).message(message);
                if (navigation.getCurrentPath() != null && !navigation.getCurrentPath().contains(PendingTradesView.class)) {
                    notification.actionButtonText("Go to \"Open trades\"")
                            .onAction(() -> {
                                preferences.dontShowAgain(key, true);
                                navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);
                                if (selectItemByTradeIdConsumer != null)
                                    UserThread.runAfter(() -> selectItemByTradeIdConsumer.accept(trade.getId()), 1);
                            })
                            .onClose(() -> preferences.dontShowAgain(key, true))
                            .show();
                } else if (selectedTradeId != null && !trade.getId().equals(selectedTradeId)) {
                    notification.actionButtonText("Select trade")
                            .onAction(() -> {
                                preferences.dontShowAgain(key, true);
                                if (selectItemByTradeIdConsumer != null)
                                    selectItemByTradeIdConsumer.accept(trade.getId());
                            })
                            .onClose(() -> preferences.dontShowAgain(key, true))
                            .show();
                }
            }
        }
    }

    private void onDisputeStateChanged(Trade trade, Trade.DisputeState disputeState) {
        Log.traceCall(disputeState.toString());
        String message = null;
        if (disputeManager.findOwnDispute(trade.getId()).isPresent()) {
            boolean supportTicket = disputeManager.findOwnDispute(trade.getId()).get().isSupportTicket();
            switch (disputeState) {
                case NONE:
                    break;
                case DISPUTE_REQUESTED:
                    break;
                case DISPUTE_STARTED_BY_PEER:
                    message = supportTicket ? "Your trading peer has opened a support ticket." : "Your trading peer has requested a dispute.";
                    break;
                case DISPUTE_CLOSED:
                    message = supportTicket ? "The support ticket hase been closed." : "The dispute has been closed.";
                    break;
            }
            if (message != null) {
                Notification notification = new Notification().disputeHeadLine(trade.getShortId()).message(message);
                if (navigation.getCurrentPath() != null && !navigation.getCurrentPath().contains(TraderDisputeView.class)) {
                    notification.actionButtonText("Go to \"Support\"")
                            .onAction(() -> navigation.navigateTo(MainView.class, DisputesView.class, TraderDisputeView.class))
                            .show();
                } else {
                    notification.show();
                }
            }
        }
    }

}
