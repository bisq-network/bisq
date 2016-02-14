package io.bitsquare.gui.main.notifications;

import com.google.inject.Inject;
import io.bitsquare.app.Log;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import javafx.collections.ListChangeListener;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationCenter {
    private static final Logger log = LoggerFactory.getLogger(NotificationCenter.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final static List<Notification> notifications = new ArrayList<>();

    static void add(Notification notification) {
        notifications.add(notification);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TradeManager tradeManager;
    private DisputeManager disputeManager;

    private final Map<String, Subscription> disputeStateSubscriptionsMap = new HashMap<>();
    private final Map<String, Subscription> tradeStateSubscriptionsMap = new HashMap<>();
    private String selectedTradeId;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public NotificationCenter(TradeManager tradeManager, DisputeManager disputeManager) {
        this.tradeManager = tradeManager;
        this.disputeManager = disputeManager;
    }

    public void onAllServicesInitialized() {
        tradeManager.getTrades().addListener((ListChangeListener<Trade>) change -> {
            change.next();
            log.error("change getRemoved " + change.getRemoved());
            log.error("change getAddedSubList " + change.getAddedSubList());
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

    public String getSelectedTradeId() {
        return selectedTradeId;
    }

    public void setSelectedTradeId(String selectedTradeId) {
        this.selectedTradeId = selectedTradeId;
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
                    message = "Your offer has been accepted by a seller.\n" +
                            "You need to wait for one blockchain confirmation before starting the payment.";
                    break;
                case DEPOSIT_CONFIRMED:
                    message = "The deposit transaction of your trade has got the first blockchain confirmation.\n" +
                            "You have to start the payment to the bitcoin seller now.";

                    break;
               /* case FIAT_PAYMENT_RECEIPT_MSG_RECEIVED:
                case PAYOUT_TX_COMMITTED:
                case PAYOUT_TX_SENT:*/
                case PAYOUT_BROAD_CASTED:
                    message = "The bitcoin seller has confirmed the receipt of your payment and the payout transaction has been published.\n" +
                            "The trade is now completed and you can withdraw your funds.";
                    break;
            }
        } else {
            switch (tradeState) {
                case DEPOSIT_PUBLISHED_MSG_RECEIVED:
                    message = "Your offer has been accepted by a buyer.\n" +
                            "You need to wait for one blockchain confirmation before starting the payment.";
                    break;
                case FIAT_PAYMENT_STARTED_MSG_RECEIVED:
                    message = "The bitcoin buyer has started the payment.\n" +
                            "Please check your payment account if you have received his payment.";
                    break;
               /* case FIAT_PAYMENT_RECEIPT_MSG_SENT:
                case PAYOUT_TX_RECEIVED:
                case PAYOUT_TX_COMMITTED:*/
                case PAYOUT_BROAD_CASTED:
                    message = "The payout transaction has been published.\n" +
                            "The trade is now completed and you can withdraw your funds.";
            }
        }

        if (message != null && !trade.getId().equals(selectedTradeId))
            new Notification().tradeHeadLine(trade.getShortId()).message(message).show();
    }

    private void onDisputeStateChanged(Trade trade, Trade.DisputeState disputeState) {
        Log.traceCall(disputeState.toString());
        String message = null;
        switch (disputeState) {
            case NONE:
                break;
            case DISPUTE_REQUESTED:
                break;
            case DISPUTE_STARTED_BY_PEER:
                if (disputeManager.findOwnDispute(trade.getId()).isPresent()) {
                    if (disputeManager.findOwnDispute(trade.getId()).get().isSupportTicket())
                        message = "Your trading peer has encountered technical problems and requested support for trade with ID " + trade.getShortId() + ".\n" +
                                "Please await further instructions from the arbitrator.\n" +
                                "Your funds are safe and will be refunded as soon the problem is resolved.";
                    else
                        message = "Your trading peer has requested a dispute for trade with ID " + trade.getShortId() + ".";
                }
                break;
            case DISPUTE_CLOSED:
                message = "A support ticket for trade with ID " + trade.getShortId() + " has been closed.";
                break;
        }
        if (message != null)
            new Notification().tradeHeadLine(trade.getShortId()).message(message).show();
    }

}
