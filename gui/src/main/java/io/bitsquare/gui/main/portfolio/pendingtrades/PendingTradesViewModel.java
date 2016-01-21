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
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.*;
import io.bitsquare.locale.BSResources;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.Trade;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import org.bitcoinj.core.BlockChainListener;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Date;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel.SellerState.*;

public class PendingTradesViewModel extends ActivatableWithDataModel<PendingTradesDataModel> implements ViewModel {
    private Subscription tradeStateSubscription;

    private interface State {
    }

    enum BuyerState implements State {
        UNDEFINED,
        WAIT_FOR_BLOCKCHAIN_CONFIRMATION,
        REQUEST_START_FIAT_PAYMENT,
        WAIT_FOR_FIAT_PAYMENT_RECEIPT,
        WAIT_FOR_UNLOCK_PAYOUT,
        REQUEST_WITHDRAWAL
    }

    enum SellerState implements State {
        UNDEFINED,
        WAIT_FOR_BLOCKCHAIN_CONFIRMATION,
        WAIT_FOR_FIAT_PAYMENT_STARTED,
        REQUEST_CONFIRM_FIAT_PAYMENT_RECEIVED,
        WAIT_FOR_PAYOUT_TX,
        WAIT_FOR_UNLOCK_PAYOUT,
        REQUEST_WITHDRAWAL
    }

    private final BSFormatter formatter;
    private final BtcAddressValidator btcAddressValidator;

    private final IBANValidator ibanValidator;
    private final BICValidator bicValidator;
    private final InputValidator inputValidator;
    private final OKPayValidator okPayValidator;
    private final AltCoinAddressValidator altCoinAddressValidator;
    private final P2PService p2PService;

    private final ObjectProperty<BuyerState> buyerState = new SimpleObjectProperty<>(PendingTradesViewModel.BuyerState.UNDEFINED);
    private final ObjectProperty<SellerState> sellerState = new SimpleObjectProperty<>(UNDEFINED);

    private final StringProperty txId = new SimpleStringProperty();
    private final BooleanProperty withdrawalButtonDisable = new SimpleBooleanProperty(true);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesViewModel(PendingTradesDataModel dataModel,
                                  BSFormatter formatter,
                                  BtcAddressValidator btcAddressValidator,
                                  IBANValidator ibanValidator,
                                  BICValidator bicValidator,
                                  InputValidator inputValidator,
                                  OKPayValidator okPayValidator,
                                  AltCoinAddressValidator altCoinAddressValidator,
                                  P2PService p2PService
    ) {
        super(dataModel);

        this.formatter = formatter;
        this.btcAddressValidator = btcAddressValidator;
        this.ibanValidator = ibanValidator;
        this.bicValidator = bicValidator;
        this.inputValidator = inputValidator;
        this.okPayValidator = okPayValidator;
        this.altCoinAddressValidator = altCoinAddressValidator;
        this.p2PService = p2PService;
    }

    @Override
    protected void activate() {
        setTradeStateSubscription();

        txId.bind(dataModel.getTxId());
    }

    @Override
    protected void deactivate() {
        if (tradeStateSubscription != null) {
            tradeStateSubscription.unsubscribe();
            tradeStateSubscription = null;
        }
        txId.unbind();
    }

    private void setTradeStateSubscription() {
        if (tradeStateSubscription != null)
            tradeStateSubscription.unsubscribe();

        if (dataModel.getTrade() != null) {
            tradeStateSubscription = EasyBind.subscribe(dataModel.getTrade().stateProperty(), newValue -> {
                if (newValue != null) {
                    applyState(newValue);
                }
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onSelectTrade(PendingTradesListItem item) {
        dataModel.onSelectTrade(item);

        // call it after  dataModel.onSelectTrade as trade is set
        setTradeStateSubscription();
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
        return dataModel.isOfferer();
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

    public boolean isAuthenticated() {
        return p2PService.isAuthenticated();
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
        return (item != null) ? formatter.getDirection(dataModel.getDirection(item.getTrade().getOffer())) : "";
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

    public long getCheckPaymentTimeAsBlockHeight() {
        return dataModel.getCheckPaymentTimeAsBlockHeight();
    }

    public long getOpenDisputeTimeAsBlockHeight() {
        return dataModel.getOpenDisputeTimeAsBlockHeight();
    }


    public int getBestChainHeight() {
        return dataModel.getBestChainHeight();
    }

    public String getDateFromBlocks(long missingBlocks) {
        return formatter.getDateFromBlocks(missingBlocks);
    }

    public String getReference() {
        return dataModel.getReference();
    }

    public String getPaymentMethod() {
        checkNotNull(dataModel.getContract(), "dataModel.getContract() must not be null");
        return BSResources.get(dataModel.getContract().getPaymentMethodName());
    }

    public String getFiatAmount() {
        return formatter.formatFiatWithCode(dataModel.getTrade().getTradeVolume());
    }

    public IBANValidator getIbanValidator() {
        return ibanValidator;
    }

    public AltCoinAddressValidator getAltCoinAddressValidator() {
        return altCoinAddressValidator;
    }

    public BICValidator getBicValidator() {
        return bicValidator;
    }

    public InputValidator getInputValidator() {
        return inputValidator;
    }

    public OKPayValidator getOkPayValidator() {
        return okPayValidator;
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
        return formatter.formatCoinWithCode(FeePolicy.getSecurityDeposit());
    }

    public boolean isBlockChainMethod() {
        return dataModel.getTrade().getOffer().getPaymentMethod().equals(PaymentMethod.BLOCK_CHAINS);
    }

    public Trade getTrade() {
        return dataModel.getTrade();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // States
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyState(Trade.State tradeState) {
        log.debug("updateSellerState (SellerTradeState) " + tradeState);
        switch (tradeState) {
            case PREPARATION:
                sellerState.set(UNDEFINED);
                buyerState.set(PendingTradesViewModel.BuyerState.UNDEFINED);
                break;

            case TAKER_FEE_PAID:
                break;

            case DEPOSIT_PUBLISH_REQUESTED:
                break;
            case DEPOSIT_PUBLISHED:
            case DEPOSIT_SEEN_IN_NETWORK:
            case DEPOSIT_PUBLISHED_MSG_SENT:
            case DEPOSIT_PUBLISHED_MSG_RECEIVED:
                sellerState.set(WAIT_FOR_BLOCKCHAIN_CONFIRMATION);
                buyerState.set(PendingTradesViewModel.BuyerState.WAIT_FOR_BLOCKCHAIN_CONFIRMATION);
                break;

            case DEPOSIT_CONFIRMED:
                sellerState.set(WAIT_FOR_FIAT_PAYMENT_STARTED);
                buyerState.set(PendingTradesViewModel.BuyerState.REQUEST_START_FIAT_PAYMENT);
                break;


            case FIAT_PAYMENT_STARTED:
                break;
            case FIAT_PAYMENT_STARTED_MSG_SENT:
                buyerState.set(PendingTradesViewModel.BuyerState.WAIT_FOR_FIAT_PAYMENT_RECEIPT);
                break;
            case FIAT_PAYMENT_STARTED_MSG_RECEIVED:
                sellerState.set(REQUEST_CONFIRM_FIAT_PAYMENT_RECEIVED);
                break;


            case FIAT_PAYMENT_RECEIPT:
                break;
            case FIAT_PAYMENT_RECEIPT_MSG_SENT:
                sellerState.set(WAIT_FOR_PAYOUT_TX);
                buyerState.set(PendingTradesViewModel.BuyerState.WAIT_FOR_FIAT_PAYMENT_RECEIPT);
                break;
            case FIAT_PAYMENT_RECEIPT_MSG_RECEIVED:
                break;


            case PAYOUT_TX_SENT:
                buyerState.set(PendingTradesViewModel.BuyerState.WAIT_FOR_UNLOCK_PAYOUT);
                break;
            case PAYOUT_TX_RECEIVED:
                break;
            case PAYOUT_TX_COMMITTED:
                sellerState.set(WAIT_FOR_UNLOCK_PAYOUT);
                break;
            case PAYOUT_BROAD_CASTED:
                sellerState.set(REQUEST_WITHDRAWAL);
                buyerState.set(PendingTradesViewModel.BuyerState.REQUEST_WITHDRAWAL);
                break;

            case WITHDRAW_COMPLETED:
                break;

            default:
                log.warn("unhandled processState " + tradeState);
                break;
        }
    }
}
