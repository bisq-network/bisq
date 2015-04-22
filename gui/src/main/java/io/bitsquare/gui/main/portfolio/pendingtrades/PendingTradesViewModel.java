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

import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcAddressValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.states.BuyerTradeState;
import io.bitsquare.trade.states.SellerTradeState;

import org.bitcoinj.core.BlockChainListener;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import java.util.Date;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesViewModel extends ActivatableWithDataModel<PendingTradesDataModel> implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesViewModel.class);

    interface State {
    }

    enum BuyerState implements State {
        UNDEFINED,
        WAIT_FOR_BLOCKCHAIN_CONFIRMATION,
        REQUEST_START_FIAT_PAYMENT,
        WAIT_FOR_FIAT_PAYMENT_RECEIPT,
        WAIT_FOR_UNLOCK_PAYOUT,
        REQUEST_WITHDRAWAL,
        CLOSED,
        FAULT
    }

    enum SellerState implements State {
        UNDEFINED,
        WAIT_FOR_BLOCKCHAIN_CONFIRMATION,
        WAIT_FOR_FIAT_PAYMENT_STARTED,
        REQUEST_CONFIRM_FIAT_PAYMENT_RECEIVED,
        WAIT_FOR_PAYOUT_TX,
        WAIT_FOR_UNLOCK_PAYOUT,
        REQUEST_WITHDRAWAL,
        CLOSED,
        FAULT
    }

    private final BSFormatter formatter;
    private final InvalidationListener sellerStateListener;
    private final InvalidationListener buyerStateListener;
    private final BtcAddressValidator btcAddressValidator;

    private final ObjectProperty<BuyerState> buyerState = new SimpleObjectProperty<>(BuyerState.UNDEFINED);
    private final ObjectProperty<SellerState> sellerState = new SimpleObjectProperty<>(SellerState.UNDEFINED);

    private final StringProperty txId = new SimpleStringProperty();
    private final BooleanProperty withdrawalButtonDisable = new SimpleBooleanProperty(true);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesViewModel(PendingTradesDataModel dataModel, BSFormatter formatter,
                                  BtcAddressValidator btcAddressValidator) {
        super(dataModel);

        this.formatter = formatter;
        this.btcAddressValidator = btcAddressValidator;
        this.sellerStateListener = (ov) -> applySellerState();
        this.buyerStateListener = (ov) -> applyBuyerState();
    }

    @Override
    public void doActivate() {
        txId.bind(dataModel.getTxId());

        dataModel.getSellerProcessState().addListener(sellerStateListener);
        dataModel.getBuyerProcessState().addListener(buyerStateListener);

        if (dataModel.getTrade() != null) {
            applySellerState();
            applyBuyerState();
        }
    }

    @Override
    public void doDeactivate() {
        txId.unbind();

        dataModel.getSellerProcessState().removeListener(sellerStateListener);
        dataModel.getBuyerProcessState().removeListener(buyerStateListener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onSelectTrade(PendingTradesListItem item) {
        dataModel.onSelectTrade(item);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ReadOnlyObjectProperty<BuyerState> getBuyerState() {
        return buyerState;
    }

    ReadOnlyObjectProperty<SellerState> getSellerState() {
        return sellerState;
    }

    public ReadOnlyStringProperty getTxId() {
        return txId;
    }

    public String getPayoutTxId() {
        return dataModel.getPayoutTxId();
    }

    public ReadOnlyBooleanProperty getWithdrawalButtonDisable() {
        return withdrawalButtonDisable;
    }

    boolean isBuyOffer() {
        return dataModel.isBuyOffer();
    }

    ReadOnlyObjectProperty<Trade> currentTrade() {
        return dataModel.getTradeProperty();
    }

    public void fiatPaymentStarted() {
        dataModel.onFiatPaymentStarted();
    }

    public void fiatPaymentReceived() {
        dataModel.onFiatPaymentReceived();
    }

    public void onWithdrawRequest(String withdrawToAddress) {
        dataModel.onWithdrawRequest(withdrawToAddress);
        if (dataModel.getSellerProcessState().get() instanceof SellerTradeState.ProcessState)
            sellerState.setValue(SellerState.CLOSED);
        else
            buyerState.setValue(BuyerState.CLOSED);
    }

    public void withdrawAddressFocusOut(String text) {
        withdrawalButtonDisable.set(!btcAddressValidator.validate(text).isValid);
    }

    public String getPayoutAmount() {
        return formatter.formatCoinWithCode(dataModel.getPayoutAmount());
    }

    ObservableList<PendingTradesListItem> getList() {
        return dataModel.getList();
    }

    public boolean isOfferer() {
        return dataModel.isOffererRole();
    }

    public WalletService getWalletService() {
        return dataModel.getWalletService();
    }

    PendingTradesListItem getSelectedItem() {
        return dataModel.getSelectedItem();
    }

    public String getCurrencyCode() {
        return dataModel.getCurrencyCode();
    }

    public BtcAddressValidator getBtcAddressValidator() {
        return btcAddressValidator;
    }

    Throwable getTradeException() {
        return dataModel.getTradeException();
    }

    String getErrorMessage() {
        return dataModel.getErrorMessage();
    }

    // columns
    String formatTradeId(String value) {
        return value;
    }

    String formatTradeAmount(Coin value) {
        return formatter.formatCoinWithCode(value);
    }

    String formatPrice(Fiat value) {
        return formatter.formatFiat(value);
    }

    String formatTradeVolume(Fiat value) {
        return formatter.formatFiatWithCode(value);
    }

    String evaluateDirection(PendingTradesListItem item) {
        return (item != null) ? formatter.formatDirection(dataModel.getDirection(item.getTrade().getOffer())) : "";
    }

    String formatDate(Date value) {
        return formatter.formatDateTime(value);
    }

    public void addBlockChainListener(BlockChainListener blockChainListener) {
        dataModel.addBlockChainListener(blockChainListener);
    }

    public void removeBlockChainListener(BlockChainListener blockChainListener) {
        dataModel.removeBlockChainListener(blockChainListener);
    }

    public long getLockTime() {
        return dataModel.getLockTime();
    }

    public int getBestChainHeight() {
        return dataModel.getBestChainHeight();
    }

    public String getUnlockDate(long missingBlocks) {
        return formatter.getUnlockDate(missingBlocks);
    }

    // payment
    public String getPaymentMethod() {
        assert dataModel.getContract() != null;
        return BSResources.get(dataModel.getContract().sellerFiatAccount.type.toString());
    }

    public String getFiatAmount() {
        return formatter.formatFiatWithCode(dataModel.getTrade().getTradeVolume());
    }

    public String getHolderName() {
        assert dataModel.getContract() != null;
        return dataModel.getContract().sellerFiatAccount.accountHolderName;
    }

    public String getPrimaryId() {
        assert dataModel.getContract() != null;
        return dataModel.getContract().sellerFiatAccount.accountPrimaryID;
    }

    public String getSecondaryId() {
        assert dataModel.getContract() != null;
        return dataModel.getContract().sellerFiatAccount.accountSecondaryID;
    }

    // summary
    public String getTradeVolume() {
        return formatter.formatCoinWithCode(dataModel.getTrade().getTradeAmount());
    }

    public String getFiatVolume() {
        return formatter.formatFiatWithCode(dataModel.getTrade().getTradeVolume());
    }

    public String getTotalFees() {
        return formatter.formatCoinWithCode(dataModel.getTotalFees());
    }

    public String getSecurityDeposit() {
        // securityDeposit is handled different for offerer and taker.
        // Offerer have paid in the max amount, but taker might have taken less so also paid in less securityDeposit
        if (dataModel.isOffererRole())
            return formatter.formatCoinWithCode(dataModel.getTrade().getOffer().getSecurityDeposit());
        else
            return formatter.formatCoinWithCode(dataModel.getTrade().getSecurityDeposit());

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // States
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applySellerState() {
        if (dataModel.getSellerProcessState().get() instanceof SellerTradeState.ProcessState) {
            SellerTradeState.ProcessState processState = (SellerTradeState.ProcessState) dataModel.getSellerProcessState().get();
            log.debug("updateSellerState (SellerTradeState) " + processState);
            if (processState != null) {
                switch (processState) {
                    case UNDEFINED:
                        sellerState.set(SellerState.UNDEFINED);
                        break;

                    case DEPOSIT_PUBLISHED_MSG_RECEIVED:
                        sellerState.set(SellerState.WAIT_FOR_BLOCKCHAIN_CONFIRMATION);
                        break;


                    case DEPOSIT_CONFIRMED:
                        sellerState.set(SellerState.WAIT_FOR_FIAT_PAYMENT_STARTED);
                        break;


                    case FIAT_PAYMENT_STARTED_MSG_RECEIVED:
                        sellerState.set(SellerState.REQUEST_CONFIRM_FIAT_PAYMENT_RECEIVED);
                        break;


                    case FIAT_PAYMENT_RECEIPT:
                        break;
                    case FIAT_PAYMENT_RECEIPT_MSG_SENT:
                        sellerState.set(SellerState.WAIT_FOR_PAYOUT_TX);
                        break;


                    case PAYOUT_TX_RECEIVED:
                        break;
                    case PAYOUT_TX_COMMITTED:
                        sellerState.set(SellerState.WAIT_FOR_UNLOCK_PAYOUT);
                        break;


                    case PAYOUT_BROAD_CASTED:
                        sellerState.set(SellerState.REQUEST_WITHDRAWAL);
                        break;


                    case FAULT:
                        sellerState.set(SellerState.FAULT);
                        break;

                    default:
                        log.warn("unhandled processState " + processState);
                        break;
                }
            }
        }
        else {
            log.error("Unhandled state " + dataModel.getSellerProcessState().get());
        }
    }

    private void applyBuyerState() {
        if (dataModel.getBuyerProcessState().get() instanceof BuyerTradeState.ProcessState) {
            BuyerTradeState.ProcessState processState = (BuyerTradeState.ProcessState) dataModel.getBuyerProcessState().get();
            log.debug("updateBuyerState (BuyerTradeState) " + processState);
            if (processState != null) {
                switch (processState) {
                    case UNDEFINED:
                        sellerState.set(SellerState.UNDEFINED);
                        break;


                    case DEPOSIT_PUBLISHED:
                    case DEPOSIT_PUBLISHED_MSG_SENT:
                        buyerState.set(BuyerState.WAIT_FOR_BLOCKCHAIN_CONFIRMATION);
                        break;


                    case DEPOSIT_CONFIRMED:
                        buyerState.set(BuyerState.REQUEST_START_FIAT_PAYMENT);
                        break;


                    case FIAT_PAYMENT_STARTED:
                        break;
                    case FIAT_PAYMENT_STARTED_MSG_SENT:
                        buyerState.set(BuyerState.WAIT_FOR_FIAT_PAYMENT_RECEIPT);
                        break;


                    case FIAT_PAYMENT_RECEIPT_MSG_RECEIVED:
                    case PAYOUT_TX_COMMITTED:
                        break;
                    case PAYOUT_TX_SENT:
                        buyerState.set(BuyerState.WAIT_FOR_UNLOCK_PAYOUT);
                        break;


                    case PAYOUT_BROAD_CASTED:
                        buyerState.set(BuyerState.REQUEST_WITHDRAWAL);
                        break;


                    case FAULT:
                        sellerState.set(SellerState.FAULT);
                        break;

                    default:
                        log.warn("unhandled viewState " + processState);
                        break;
                }
            }
        }
        else {
            log.error("Unhandled state " + dataModel.getBuyerProcessState().get());
        }
    }
}
