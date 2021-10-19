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

package bisq.desktop.main.overlays.notifications;

import bisq.desktop.Navigation;
import bisq.desktop.main.MainView;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesView;
import bisq.desktop.main.support.SupportView;
import bisq.desktop.main.support.dispute.client.DisputeClientView;
import bisq.desktop.main.support.dispute.client.mediation.MediationClientView;
import bisq.desktop.main.support.dispute.client.refund.RefundClientView;

import bisq.core.locale.Res;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.MakerTrade;
import bisq.core.trade.model.bisq_v1.BuyerTrade;
import bisq.core.trade.model.bisq_v1.SellerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;

import bisq.common.UserThread;

import com.google.inject.Inject;

import javax.inject.Singleton;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.collections.ListChangeListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Singleton
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
    private final MediationManager mediationManager;
    private final RefundManager refundManager;
    private final Navigation navigation;

    private final Map<String, Subscription> disputeStateSubscriptionsMap = new HashMap<>();
    private final Map<String, Subscription> tradePhaseSubscriptionsMap = new HashMap<>();
    @Nullable
    private String selectedTradeId;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public NotificationCenter(TradeManager tradeManager,
                              MediationManager mediationManager,
                              RefundManager refundManager,
                              Preferences preferences,
                              Navigation navigation) {
        this.tradeManager = tradeManager;
        this.mediationManager = mediationManager;
        this.refundManager = refundManager;
        this.navigation = navigation;

        EasyBind.subscribe(preferences.getUseAnimationsProperty(), useAnimations -> NotificationCenter.useAnimations = useAnimations);
    }

    public void onAllServicesAndViewsInitialized() {
        tradeManager.getObservableList().addListener((ListChangeListener<Trade>) change -> {
            change.next();
            if (change.wasRemoved()) {
                change.getRemoved().forEach(trade -> {
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
                change.getAddedSubList().forEach(trade -> {
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

        tradeManager.getObservableList().forEach(trade -> {
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
        String message = null;
        if (refundManager.findOwnDispute(trade.getId()).isPresent()) {
            String disputeOrTicket = refundManager.findOwnDispute(trade.getId()).get().isSupportTicket() ?
                    Res.get("shared.supportTicket") :
                    Res.get("shared.dispute");
            switch (disputeState) {
                case NO_DISPUTE:
                    break;
                case REFUND_REQUESTED:
                    break;
                case REFUND_REQUEST_STARTED_BY_PEER:
                    message = Res.get("notification.trade.peerOpenedDispute", disputeOrTicket);
                    break;
                case REFUND_REQUEST_CLOSED:
                    message = Res.get("notification.trade.disputeClosed", disputeOrTicket);
                    break;
                default:
//                    if (DevEnv.isDevMode()) {
//                        log.error("refundManager must not contain mediation or arbitration disputes. disputeState={}", disputeState);
//                        throw new RuntimeException("arbitrationDisputeManager must not contain mediation disputes");
//                    }
                    break;
            }
            if (message != null) {
                goToSupport(trade, message, false);
            }
        } else if (mediationManager.findOwnDispute(trade.getId()).isPresent()) {
            String disputeOrTicket = mediationManager.findOwnDispute(trade.getId()).get().isSupportTicket() ?
                    Res.get("shared.supportTicket") :
                    Res.get("shared.mediationCase");
            switch (disputeState) {
                // TODO
                case MEDIATION_REQUESTED:
                    break;
                case MEDIATION_STARTED_BY_PEER:
                    message = Res.get("notification.trade.peerOpenedDispute", disputeOrTicket);
                    break;
                case MEDIATION_CLOSED:
                    message = Res.get("notification.trade.disputeClosed", disputeOrTicket);
                    break;
                default:
//                    if (DevEnv.isDevMode()) {
//                        log.error("mediationDisputeManager must not contain arbitration or refund disputes. disputeState={}", disputeState);
//                        throw new RuntimeException("mediationDisputeManager must not contain arbitration disputes");
//                    }
                    break;
            }
            if (message != null) {
                goToSupport(trade, message, true);
            }
        }
    }

    private void goToSupport(Trade trade, String message, boolean isMediation) {
        Notification notification = new Notification().disputeHeadLine(trade.getShortId()).message(message);
        Class<? extends DisputeClientView> viewClass = isMediation ?
                MediationClientView.class :
                RefundClientView.class;
        if (navigation.getCurrentPath() != null && !navigation.getCurrentPath().contains(viewClass)) {
            notification.actionButtonTextWithGoTo("navigation.support")
                    .onAction(() -> navigation.navigateTo(MainView.class, SupportView.class, viewClass))
                    .show();
        } else {
            notification.show();
        }
    }

}
