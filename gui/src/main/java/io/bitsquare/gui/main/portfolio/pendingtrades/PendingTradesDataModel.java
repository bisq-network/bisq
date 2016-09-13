/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.portfolio.pendingtrades;

import com.google.inject.Inject;
import io.bitsquare.app.Log;
import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.arbitration.Dispute;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.FaultHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ActivatableDataModel;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.disputes.DisputesView;
import io.bitsquare.gui.main.overlays.notifications.NotificationCenter;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.SelectDepositTxWindow;
import io.bitsquare.gui.main.overlays.windows.WalletPasswordWindow;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.trade.BuyerTrade;
import io.bitsquare.trade.SellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.bitcoinj.core.BlockChainListener;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PendingTradesDataModel extends ActivatableDataModel {
    public final TradeManager tradeManager;
    public final WalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final User user;
    private final KeyRing keyRing;
    public final DisputeManager disputeManager;
    private P2PService p2PService;
    public final Navigation navigation;
    public final WalletPasswordWindow walletPasswordWindow;
    private final NotificationCenter notificationCenter;

    final ObservableList<PendingTradesListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<Trade> tradesListChangeListener;
    private boolean isOfferer;

    final ObjectProperty<PendingTradesListItem> selectedItemProperty = new SimpleObjectProperty<>();
    public final StringProperty txId = new SimpleStringProperty();
    public final Preferences preferences;
    private boolean activated;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesDataModel(TradeManager tradeManager, WalletService walletService, TradeWalletService tradeWalletService,
                                  User user, KeyRing keyRing, DisputeManager disputeManager, Preferences preferences, P2PService p2PService,
                                  Navigation navigation, WalletPasswordWindow walletPasswordWindow, NotificationCenter notificationCenter) {
        this.tradeManager = tradeManager;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.user = user;
        this.keyRing = keyRing;
        this.disputeManager = disputeManager;
        this.preferences = preferences;
        this.p2PService = p2PService;
        this.navigation = navigation;
        this.walletPasswordWindow = walletPasswordWindow;
        this.notificationCenter = notificationCenter;

        tradesListChangeListener = change -> onListChanged();
        notificationCenter.setSelectItemByTradeIdConsumer(this::selectItemByTradeId);
    }

    @Override
    protected void activate() {
        tradeManager.getTrades().addListener(tradesListChangeListener);
        onListChanged();
        if (selectedItemProperty.get() != null)
            notificationCenter.setSelectedTradeId(selectedItemProperty.get().getTrade().getId());

        activated = true;
    }

    @Override
    protected void deactivate() {
        tradeManager.getTrades().removeListener(tradesListChangeListener);
        notificationCenter.setSelectedTradeId(null);
        activated = false;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onSelectItem(PendingTradesListItem item) {
        doSelectItem(item);
    }

    public void onPaymentStarted(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        checkNotNull(getTrade(), "trade must not be null");
        checkArgument(getTrade() instanceof BuyerTrade, "Check failed: trade instanceof BuyerTrade");
        checkArgument(getTrade().getDisputeState() == Trade.DisputeState.NONE, "Check failed: trade.getDisputeState() == Trade.DisputeState.NONE");
        ((BuyerTrade) getTrade()).onFiatPaymentStarted(resultHandler, errorMessageHandler);
    }

    public void onFiatPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        checkNotNull(getTrade(), "trade must not be null");
        checkArgument(getTrade() instanceof SellerTrade, "Check failed: trade not instanceof SellerTrade");
        if (getTrade().getDisputeState() == Trade.DisputeState.NONE)
            ((SellerTrade) getTrade()).onFiatPaymentReceived(resultHandler, errorMessageHandler);
    }

    public void onWithdrawRequest(String toAddress, Coin receiverAmount, KeyParameter aesKey, ResultHandler resultHandler, FaultHandler faultHandler) {
        checkNotNull(getTrade(), "trade must not be null");

        if (toAddress != null && toAddress.length() > 0) {
            tradeManager.onWithdrawRequest(
                    toAddress,
                    receiverAmount,
                    aesKey,
                    getTrade(),
                    () -> {
                        resultHandler.handleResult();
                        selectBestItem();
                    },
                    (errorMessage, throwable) -> {
                        log.error(errorMessage);
                        faultHandler.handleFault(errorMessage, throwable);
                    });
        } else {
            faultHandler.handleFault("No receiver address defined", null);
        }
    }

    public void onOpenDispute() {
        tryOpenDispute(false);
    }

    public void onOpenSupportTicket() {
        tryOpenDispute(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public PendingTradesListItem getSelectedItem() {
        return selectedItemProperty.get() != null ? selectedItemProperty.get() : null;
    }

    @Nullable
    public Trade getTrade() {
        return selectedItemProperty.get() != null ? selectedItemProperty.get().getTrade() : null;
    }

    @Nullable
    Offer getOffer() {
        return getTrade() != null ? getTrade().getOffer() : null;
    }

    boolean isBuyOffer() {
        return getOffer() != null && getOffer().getDirection() == Offer.Direction.BUY;
    }

    boolean isOfferer(Offer offer) {
        return tradeManager.isMyOffer(offer);
    }

    boolean isOfferer() {
        return isOfferer;
    }

    Coin getTotalFees() {
        return FeePolicy.getFixedTxFeeForTrades().add(isOfferer() ? FeePolicy.getCreateOfferFee() : FeePolicy.getTakeOfferFee());
    }

    public String getCurrencyCode() {
        return getOffer() != null ? getOffer().getCurrencyCode() : "";
    }

    public Offer.Direction getDirection(Offer offer) {
        isOfferer = tradeManager.isMyOffer(offer);
        return isOfferer ? offer.getDirection() : offer.getMirroredDirection();
    }

    void addBlockChainListener(BlockChainListener blockChainListener) {
        tradeWalletService.addBlockChainListener(blockChainListener);
    }

    void removeBlockChainListener(BlockChainListener blockChainListener) {
        tradeWalletService.removeBlockChainListener(blockChainListener);
    }

    public long getLockTime() {
        return getTrade() != null ? getTrade().getLockTimeAsBlockHeight() : 0;
    }

    public int getBestChainHeight() {
        return tradeWalletService.getBestChainHeight();
    }

    @Nullable
    public PaymentAccountContractData getSellersPaymentAccountContractData() {
        if (getTrade() != null && getTrade().getContract() != null)
            return getTrade().getContract().getSellerPaymentAccountContractData();
        else
            return null;
    }

    public String getReference() {
        return getOffer() != null ? getOffer().getReferenceText() : "";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onListChanged() {
        Log.traceCall();
        list.clear();
        list.addAll(tradeManager.getTrades().stream().map(PendingTradesListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTrade().getDate().compareTo(o1.getTrade().getDate()));

        selectBestItem();
    }

    private void selectBestItem() {
        if (list.size() == 1)
            doSelectItem(list.get(0));
        else if (list.size() > 1 && (selectedItemProperty.get() == null || !list.contains(selectedItemProperty.get())))
            doSelectItem(list.get(0));
        else if (list.size() == 0)
            doSelectItem(null);
    }

    private void selectItemByTradeId(String tradeId) {
        if (activated)
            list.stream().filter(e -> e.getTrade().getId().equals(tradeId)).findAny().ifPresent(this::doSelectItem);
    }

    private void doSelectItem(PendingTradesListItem item) {
        if (item != null) {
            Trade trade = item.getTrade();
            isOfferer = tradeManager.isMyOffer(trade.getOffer());
            if (trade.getDepositTx() != null)
                txId.set(trade.getDepositTx().getHashAsString());
            notificationCenter.setSelectedTradeId(trade.getId());
        } else {
            notificationCenter.setSelectedTradeId(null);
        }
        selectedItemProperty.set(item);
    }

    private void tryOpenDispute(boolean isSupportTicket) {
        if (getTrade() != null) {
            Transaction depositTx = getTrade().getDepositTx();
            if (depositTx != null) {
                doOpenDispute(isSupportTicket, getTrade().getDepositTx());
            } else {
                log.info("Trade.depositTx is null. We try to find the tx in our wallet.");
                List<Transaction> candidates = new ArrayList<>();
                List<Transaction> transactions = walletService.getWallet().getRecentTransactions(100, true);
                transactions.stream().forEach(transaction -> {
                    Coin valueSentFromMe = transaction.getValueSentFromMe(walletService.getWallet());
                    if (!valueSentFromMe.isZero()) {
                        // spending tx
                        // MS tx
                        candidates.addAll(transaction.getOutputs().stream()
                                .filter(transactionOutput -> !transactionOutput.isMine(walletService.getWallet()))
                                .filter(transactionOutput -> transactionOutput.getScriptPubKey().isPayToScriptHash())
                                .map(transactionOutput -> transaction)
                                .collect(Collectors.toList()));
                    }
                });

                if (candidates.size() == 1)
                    doOpenDispute(isSupportTicket, candidates.get(0));
                else if (candidates.size() > 1)
                    new SelectDepositTxWindow().transactions(candidates)
                            .onSelect(transaction -> doOpenDispute(isSupportTicket, transaction))
                            .closeButtonText("Cancel")
                            .show();
                else
                    log.error("Trade.depositTx is null and we did not find any MultiSig transaction.");
            }
        } else {
            log.error("Trade is null");
        }
    }

    private void doOpenDispute(boolean isSupportTicket, Transaction depositTx) {
        Log.traceCall("depositTx=" + depositTx);
        byte[] depositTxSerialized = null;
        byte[] payoutTxSerialized = null;
        String depositTxHashAsString = null;
        String payoutTxHashAsString = null;
        if (depositTx != null) {
            depositTxSerialized = depositTx.bitcoinSerialize();
            depositTxHashAsString = depositTx.getHashAsString();
        } else {
            log.warn("depositTx is null");
        }
        Trade trade = getTrade();
        if (trade != null) {
            Transaction payoutTx = trade.getPayoutTx();
            if (payoutTx != null) {
                payoutTxSerialized = payoutTx.bitcoinSerialize();
                payoutTxHashAsString = payoutTx.getHashAsString();
            } else {
                log.debug("payoutTx is null at doOpenDispute");
            }

            final Arbitrator acceptedArbitratorByAddress = user.getAcceptedArbitratorByAddress(trade.getArbitratorNodeAddress());
            checkNotNull(acceptedArbitratorByAddress);
            Dispute dispute = new Dispute(disputeManager.getDisputeStorage(),
                    trade.getId(),
                    keyRing.getPubKeyRing().hashCode(), // traderId
                    trade.getOffer().getDirection() == Offer.Direction.BUY ? isOfferer : !isOfferer,
                    isOfferer,
                    keyRing.getPubKeyRing(),
                    trade.getDate(),
                    trade.getContract(),
                    trade.getContractHash(),
                    depositTxSerialized,
                    payoutTxSerialized,
                    depositTxHashAsString,
                    payoutTxHashAsString,
                    trade.getContractAsJson(),
                    trade.getOffererContractSignature(),
                    trade.getTakerContractSignature(),
                    acceptedArbitratorByAddress.getPubKeyRing(),
                    isSupportTicket
            );

            trade.setDisputeState(Trade.DisputeState.DISPUTE_REQUESTED);
            if (p2PService.isBootstrapped()) {
                disputeManager.sendOpenNewDisputeMessage(dispute,
                        () -> navigation.navigateTo(MainView.class, DisputesView.class),
                        errorMessage -> new Popup().warning(errorMessage).show());
            } else {
                new Popup().information("You need to wait until you are fully connected to the network.\n" +
                        "That might take up to about 2 minutes at startup.").show();
            }
        } else {
            log.warn("trade is null at doOpenDispute");
        }
    }
}

