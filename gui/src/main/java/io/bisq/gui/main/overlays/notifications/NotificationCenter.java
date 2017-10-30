package io.bisq.gui.main.overlays.notifications;

import com.google.inject.Inject;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.locale.Res;
import io.bisq.core.arbitration.DisputeManager;
import io.bisq.core.trade.*;
import io.bisq.core.user.DontShowAgainLookup;
import io.bisq.core.user.Preferences;
import io.bisq.gui.Navigation;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.disputes.DisputesView;
import io.bisq.gui.main.disputes.trader.TraderDisputeView;
import io.bisq.gui.main.portfolio.PortfolioView;
import io.bisq.gui.main.portfolio.pendingtrades.PendingTradesView;
import javafx.collections.ListChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class NotificationCenter {


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
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
    private final Map<String, Subscription> tradePhaseSubscriptionsMap = new HashMap<>();
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

        EasyBind.subscribe(preferences.getUseAnimationsProperty(), useAnimations -> NotificationCenter.useAnimations = useAnimations);
    }

    public void onAllServicesAndViewsInitialized() {
        tradeManager.getTradableList().addListener((ListChangeListener<Trade>) change -> {
            change.next();
            if (change.wasRemoved()) {
                change.getRemoved().stream().forEach(trade -> {
                    String tradeId = trade.getId();
                    if (disputeStateSubscriptionsMap.containsKey(tradeId)) {
                        disputeStateSubscriptionsMap.get(tradeId).unsubscribe();
                        disputeStateSubscriptionsMap.remove(tradeId);
                    }

                    if (tradePhaseSubscriptionsMap.containsKey(tradeId)) {
                        tradePhaseSubscriptionsMap.get(tradeId).unsubscribe();
                        tradePhaseSubscriptionsMap.remove(tradeId);
                    }
                });
            }
            if (change.wasAdded()) {
                change.getAddedSubList().stream().forEach(trade -> {
                    String tradeId = trade.getId();
                    if (disputeStateSubscriptionsMap.containsKey(tradeId)) {
                        log.debug("We have already an entry in disputeStateSubscriptionsMap.");
                    } else {
                        Subscription disputeStateSubscription = EasyBind.subscribe(trade.disputeStateProperty(),
                            disputeState -> onDisputeStateChanged(trade, disputeState));
                        disputeStateSubscriptionsMap.put(tradeId, disputeStateSubscription);
                    }

                    if (tradePhaseSubscriptionsMap.containsKey(tradeId)) {
                        log.debug("We have already an entry in tradePhaseSubscriptionsMap.");
                    } else {
                        Subscription tradePhaseSubscription = EasyBind.subscribe(trade.statePhaseProperty(),
                            phase -> onTradePhaseChanged(trade, phase));
                        tradePhaseSubscriptionsMap.put(tradeId, tradePhaseSubscription);
                    }
                });
            }
        });

        tradeManager.getTradableList().stream()
            .forEach(trade -> {
                    String tradeId = trade.getId();
                    Subscription disputeStateSubscription = EasyBind.subscribe(trade.disputeStateProperty(),
                        disputeState -> onDisputeStateChanged(trade, disputeState));
                    disputeStateSubscriptionsMap.put(tradeId, disputeStateSubscription);

                    Subscription tradePhaseSubscription = EasyBind.subscribe(trade.statePhaseProperty(),
                        phase -> onTradePhaseChanged(trade, phase));
                    tradePhaseSubscriptionsMap.put(tradeId, tradePhaseSubscription);
                }
            );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter/Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
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


    private void onTradePhaseChanged(Trade trade, Trade.Phase phase) {
        String message = null;
        if (trade.isPayoutPublished() && !trade.isWithdrawn()) {
            message = Res.get("notification.trade.completed");
        } else {
            if (trade instanceof MakerTrade &&
                phase.ordinal() == Trade.Phase.DEPOSIT_PUBLISHED.ordinal()) {
                final String role = trade instanceof BuyerTrade ? Res.get("shared.seller") : Res.get("shared.buyer");
                message = Res.get("notification.trade.accepted", role);
            }

            if (trade instanceof BuyerTrade && phase.ordinal() == Trade.Phase.DEPOSIT_CONFIRMED.ordinal())
                message = Res.get("notification.trade.confirmed");
            else if (trade instanceof SellerTrade && phase.ordinal() == Trade.Phase.FIAT_SENT.ordinal())
                message = Res.get("notification.trade.paymentStarted");
        }

        if (message != null) {
            String key = "NotificationCenter_" + phase.name() + trade.getId();
            if (DontShowAgainLookup.showAgain(key)) {
                Notification notification = new Notification().tradeHeadLine(trade.getShortId()).message(message);
                if (navigation.getCurrentPath() != null && !navigation.getCurrentPath().contains(PendingTradesView.class)) {
                    notification.actionButtonTextWithGoTo("navigation.portfolio.pending")
                        .onAction(() -> {
                            DontShowAgainLookup.dontShowAgain(key, true);
                            //noinspection unchecked
                            navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);
                            if (selectItemByTradeIdConsumer != null)
                                UserThread.runAfter(() -> selectItemByTradeIdConsumer.accept(trade.getId()), 1);
                        })
                        .onClose(() -> DontShowAgainLookup.dontShowAgain(key, true))
                        .show();
                } else if (selectedTradeId != null && !trade.getId().equals(selectedTradeId)) {
                    notification.actionButtonText(Res.get("notification.trade.selectTrade"))
                        .onAction(() -> {
                            DontShowAgainLookup.dontShowAgain(key, true);
                            if (selectItemByTradeIdConsumer != null)
                                selectItemByTradeIdConsumer.accept(trade.getId());
                        })
                        .onClose(() -> DontShowAgainLookup.dontShowAgain(key, true))
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
                case NO_DISPUTE:
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
                    //noinspection unchecked
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
