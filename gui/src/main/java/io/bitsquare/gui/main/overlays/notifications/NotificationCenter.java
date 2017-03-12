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
import io.bitsquare.locale.Res;
import io.bitsquare.messages.user.Preferences;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
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
            message = Res.get("notification.trade.completed");
        } else {
            if (tradeManager.isBuyer(trade.getOffer())) {
                switch (tradeState) {
                    case OFFERER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG:
                        message = Res.get("notification.trade.accepted", Res.get("shared.seller"));
                        break;
                    case DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN:
                        message = Res.get("notification.trade.confirmed");
                        break;
                }
            } else {
                switch (tradeState) {
                    case OFFERER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG:
                        message = Res.get("notification.trade.accepted", Res.get("shared.buyer"));
                        break;
                    case SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG:
                        message = Res.get("notification.trade.paymentStarted");
                        break;
                }
            }
        }

        if (message != null) {
            String key = tradeState.name() + trade.getId();
            if (preferences.showAgain(key)) {
                Notification notification = new Notification().tradeHeadLine(trade.getShortId()).message(message);
                if (navigation.getCurrentPath() != null && !navigation.getCurrentPath().contains(PendingTradesView.class)) {
                    notification.actionButtonTextWithGoTo("navigation.portfolio.pending")
                            .onAction(() -> {
                                preferences.dontShowAgain(key, true);
                                navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);
                                if (selectItemByTradeIdConsumer != null)
                                    UserThread.runAfter(() -> selectItemByTradeIdConsumer.accept(trade.getId()), 1);
                            })
                            .onClose(() -> preferences.dontShowAgain(key, true))
                            .show();
                } else if (selectedTradeId != null && !trade.getId().equals(selectedTradeId)) {
                    notification.actionButtonText(Res.get("notification.trade.selectTrade"))
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
            String disputeOrTicket = disputeManager.findOwnDispute(trade.getId()).get().isSupportTicket() ?
                    Res.get("shared.supportTicket") :
                    Res.get("shared.dispute");
            switch (disputeState) {
                case NONE:
                    break;
                case DISPUTE_REQUESTED:
                    break;
                case DISPUTE_STARTED_BY_PEER:
                    message = Res.get("notification.trade.peerOpenedDispute", disputeOrTicket);
                    break;
                case DISPUTE_CLOSED:
                    message = Res.get("notification.trade.disputeClosed", disputeOrTicket);
                    break;
            }
            if (message != null) {
                Notification notification = new Notification().disputeHeadLine(trade.getShortId()).message(message);
                if (navigation.getCurrentPath() != null && !navigation.getCurrentPath().contains(TraderDisputeView.class)) {
                    notification.actionButtonTextWithGoTo("navigation.support")
                            .onAction(() -> navigation.navigateTo(MainView.class, DisputesView.class, TraderDisputeView.class))
                            .show();
                } else {
                    notification.show();
                }
            }
        }
    }

}
