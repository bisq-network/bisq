package io.bitsquare.gui.main.notifications;

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
                        log.warn("We have already an entry in disputeStateSubscriptionsMap. That should never happen.");
                    } else {
                        Subscription disputeStateSubscription = EasyBind.subscribe(trade.disputeStateProperty(), disputeState -> onDisputeStateChanged(trade, disputeState));
                        disputeStateSubscriptionsMap.put(tradeId, disputeStateSubscription);
                    }

                    if (tradeStateSubscriptionsMap.containsKey(tradeId)) {
                        log.warn("We have already an entry in tradeStateSubscriptionsMap. That should never happen.");
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
        if (tradeManager.isBuyer(trade.getOffer())) {
            switch (tradeState) {
                case DEPOSIT_PUBLISHED_MSG_RECEIVED:
                    message = "Your offer has been accepted by a seller.";
                    break;
                case DEPOSIT_CONFIRMED:
                    message = "Your trade has at least one blockchain confirmation.\n" +
                            "You can start the payment now.";

                    break;
               /* case FIAT_PAYMENT_RECEIPT_MSG_RECEIVED:
                case PAYOUT_TX_COMMITTED:
                case PAYOUT_TX_SENT:*/
                case PAYOUT_BROAD_CASTED:
                    message = "The trade is now completed and you can withdraw your funds.";
                    break;
            }
        } else {
            switch (tradeState) {
                case DEPOSIT_PUBLISHED_MSG_RECEIVED:
                    message = "Your offer has been accepted by a buyer.";
                    break;
                case FIAT_PAYMENT_STARTED_MSG_RECEIVED:
                    message = "The bitcoin buyer has started the payment.";
                    break;
               /* case FIAT_PAYMENT_RECEIPT_MSG_SENT:
                case PAYOUT_TX_RECEIVED:
                case PAYOUT_TX_COMMITTED:*/
                case PAYOUT_BROAD_CASTED:
                    message = "The trade is now completed and you can withdraw your funds.";
            }
        }

        if (message != null) {
            Notification notification = new Notification().tradeHeadLine(trade.getShortId()).message(message);

            if (navigation.getCurrentPath() != null && !navigation.getCurrentPath().contains(PendingTradesView.class)) {
                notification.actionButtonText("Go to \"Open trades\"")
                        .onAction(() -> {
                            navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);
                            UserThread.runAfter(() -> {
                                selectItemByTradeIdConsumer.accept(trade.getId());
                            }, 1);
                        })
                        .show();
            } else if (selectedTradeId != null && !trade.getId().equals(selectedTradeId)) {
                notification.actionButtonText("Select trade")
                        .onAction(() -> selectItemByTradeIdConsumer.accept(trade.getId()))
                        .show();
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
