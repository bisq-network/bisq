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

package bisq.desktop.main.funds.transactions;

import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.filtering.FilterableListItem;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;

import com.google.common.base.Suppliers;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.Tooltip;

import java.util.Date;
import java.util.Optional;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
class TransactionsListItem implements FilterableListItem {
    private final CoinFormatter formatter;
    private String dateString;
    private final Date date;
    private final String txId;
    @Nullable
    private Tradable tradable;
    private String details = "";
    private String addressString = "";
    private String direction = "";
    private boolean received;
    private Coin amountAsCoin = Coin.ZERO;
    private String memo = "";
    @Getter
    private final boolean isDustAttackTx;
    private boolean initialTxConfidenceVisibility = true;
    private final Supplier<LazyFields> lazyFieldsSupplier;

    private static class LazyFields {
        int confirmations;
        TxConfidenceIndicator txConfidenceIndicator;
        Tooltip tooltip;
    }

    private LazyFields lazy() {
        return lazyFieldsSupplier.get();
    }

    // used at exportCSV
    TransactionsListItem() {
        date = null;
        txId = null;
        formatter = null;
        isDustAttackTx = false;
        lazyFieldsSupplier = null;
    }

    TransactionsListItem(Transaction transaction,
                         BtcWalletService btcWalletService,
                         BsqWalletService bsqWalletService,
                         TransactionAwareTradable transactionAwareTradable,
                         DaoFacade daoFacade,
                         CoinFormatter formatter,
                         long ignoreDustThreshold) {
        this.formatter = formatter;
        this.memo = transaction.getMemo();

        txId = transaction.getTxId().toString();

        Optional<Tradable> optionalTradable = Optional.ofNullable(transactionAwareTradable)
                .map(TransactionAwareTradable::asTradable);

        Coin valueSentToMe = btcWalletService.getValueSentToMeForTransaction(transaction);
        Coin valueSentFromMe = btcWalletService.getValueSentFromMeForTransaction(transaction);

        // TODO check and refactor
        boolean txFeeForBsqPayment = false;
        boolean withdrawalFromBSQWallet = false;
        if (valueSentToMe.isZero()) {
            amountAsCoin = valueSentFromMe.multiply(-1);
            for (TransactionOutput output : transaction.getOutputs()) {
                if (!btcWalletService.isTransactionOutputMine(output)) {
                    received = false;
                    if (WalletService.isOutputScriptConvertibleToAddress(output)) {
                        addressString = WalletService.getAddressStringFromOutput(output);
                        if (bsqWalletService.isTransactionOutputMine(output)) {
                            txFeeForBsqPayment = true;
                        } else {
                            direction = Res.get("funds.tx.direction.sentTo");
                        }
                        break;
                    }
                }
            }
        } else if (valueSentFromMe.isZero()) {
            amountAsCoin = valueSentToMe;
            direction = Res.get("funds.tx.direction.receivedWith");
            received = true;
            for (TransactionOutput output : transaction.getOutputs()) {
                if (btcWalletService.isTransactionOutputMine(output) &&
                        WalletService.isOutputScriptConvertibleToAddress(output)) {
                    addressString = WalletService.getAddressStringFromOutput(output);
                    break;
                }
            }
        } else {
            amountAsCoin = valueSentToMe.subtract(valueSentFromMe);
            boolean outgoing = false;
            for (TransactionOutput output : transaction.getOutputs()) {
                if (!btcWalletService.isTransactionOutputMine(output)) {
                    if (WalletService.isOutputScriptConvertibleToAddress(output)) {
                        addressString = WalletService.getAddressStringFromOutput(output);
                        if (bsqWalletService.isTransactionOutputMine(output)) {
                            outgoing = false;
                            txFeeForBsqPayment = true;

                            Optional<TxType> txTypeOptional = daoFacade.getOptionalTxType(txId);
                            if (txTypeOptional.isPresent()) {
                                if (txTypeOptional.get().equals(TxType.COMPENSATION_REQUEST))
                                    details = Res.get("funds.tx.compensationRequestTxFee");
                                else if (txTypeOptional.get().equals(TxType.REIMBURSEMENT_REQUEST))
                                    details = Res.get("funds.tx.reimbursementRequestTxFee");
                                else
                                    details = Res.get("funds.tx.daoTxFee");
                            }
                        } else {
                            outgoing = true;
                        }
                        break;
                    }
                } else {
                    addressString = WalletService.getAddressStringFromOutput(output);
                    outgoing = (valueSentToMe.getValue() < valueSentFromMe.getValue());
                    if (!outgoing) {
                        direction = Res.get("funds.tx.direction.receivedWith");
                        received = true;
                        withdrawalFromBSQWallet = true;
                    }
                }
            }

            if (outgoing) {
                direction = Res.get("funds.tx.direction.sentTo");
                received = false;
            }
        }

        if (txFeeForBsqPayment) {
            // direction = Res.get("funds.tx.txFeePaymentForBsqTx");
            direction = Res.get("funds.tx.direction.sentTo");
            //addressString = "";
        }

        if (optionalTradable.isPresent()) {
            tradable = optionalTradable.get();
            if (tradable instanceof OpenOffer) {
                details = Res.get("funds.tx.createOfferFee");
            } else if (tradable instanceof Trade) {
                Trade trade = (Trade) tradable;
                TransactionAwareTrade transactionAwareTrade = (TransactionAwareTrade) transactionAwareTradable;
                if (trade.getTakerFeeTxId() != null && trade.getTakerFeeTxId().equals(txId)) {
                    details = Res.get("funds.tx.takeOfferFee");
                } else {
                    Offer offer = trade.getOffer();
                    String offerFeePaymentTxID = offer.getOfferFeePaymentTxId();
                    if (offerFeePaymentTxID != null && offerFeePaymentTxID.equals(txId)) {
                        details = Res.get("funds.tx.createOfferFee");
                    } else if (trade.getDepositTx() != null &&
                            trade.getDepositTx().getTxId().equals(Sha256Hash.wrap(txId))) {
                        details = Res.get("funds.tx.multiSigDeposit");
                    } else if (trade.getPayoutTx() != null &&
                            trade.getPayoutTx().getTxId().equals(Sha256Hash.wrap(txId)) &&
                            !transactionAwareTrade.isClaimTx(txId)) {
                        details = Res.get("funds.tx.multiSigPayout");
                        if (amountAsCoin.isZero()) {
                            initialTxConfidenceVisibility = false;
                        }
                    } else {
                        Trade.DisputeState disputeState = trade.getDisputeState();
                        if (disputeState == Trade.DisputeState.DISPUTE_CLOSED) {
                            if (valueSentToMe.isPositive()) {
                                details = Res.get("funds.tx.disputePayout");
                            } else {
                                details = Res.get("funds.tx.disputeLost");
                                initialTxConfidenceVisibility = false;
                            }
                        } else if ((disputeState == Trade.DisputeState.REFUND_REQUEST_CLOSED ||
                                disputeState == Trade.DisputeState.REFUND_REQUESTED ||
                                disputeState == Trade.DisputeState.REFUND_REQUEST_STARTED_BY_PEER) && !trade.hasV5Protocol()) {
                            if (valueSentToMe.isPositive()) { // FIXME: This will give a false positive if user is a BM.
                                details = Res.get("funds.tx.refund");
                            } else {
                                // We have spent the deposit tx outputs to the Bisq donation address to enable
                                // the refund process (refund agent -> reimbursement). As the funds have left our wallet
                                // already when funding the deposit tx we show 0 BTC as amount.
                                // Confirmation is not known from the BitcoinJ side (not 100% clear why) as no funds
                                // left our wallet nor we received funds. So we set indicator invisible.
                                amountAsCoin = Coin.ZERO;
                                details = Res.get("funds.tx.collateralForRefund");
                                initialTxConfidenceVisibility = false;
                            }
                        } else {
                            if (transactionAwareTrade.isWarningTx(txId)) {
                                details = valueSentToMe.isPositive()
                                        ? Res.get("funds.tx.warningTx")
                                        : Res.get("funds.tx.peersWarningTx");
                            } else if (transactionAwareTrade.isRedirectTx(txId)) {
                                details = Res.get("funds.tx.collateralForRefund");
                            } else if (transactionAwareTrade.isClaimTx(txId)) {
                                details = valueSentToMe.isPositive()
                                        ? Res.get("funds.tx.claimTx")
                                        : Res.get("funds.tx.peersClaimTx");
                            } else if (transactionAwareTrade.isDelayedPayoutTx(txId)) {
                                details = Res.get("funds.tx.timeLockedPayoutTx");
                                initialTxConfidenceVisibility = false;
                            } else {
                                details = Res.get("funds.tx.unknown");
                            }
                        }
                    }
                }
            } else if (tradable instanceof BsqSwapTrade) {
                direction = amountAsCoin.isPositive() ? Res.get("funds.tx.bsqSwapBuy") :
                        Res.get("funds.tx.bsqSwapSell");

                // Find my BTC output address
                var tx = btcWalletService.getTransaction(((BsqSwapTrade) tradable).getTxId());
                addressString = tx != null ?
                        tx.getOutputs().stream()
                                .filter(output -> output.isMine(btcWalletService.getWallet()))
                                .map(output -> output.getScriptPubKey().getToAddress(btcWalletService.getParams()))
                                .map(Object::toString)
                                .findFirst()
                                .orElse("") :
                        "";
                details = Res.get("funds.tx.bsqSwapTx");
            }
        } else {
            if (amountAsCoin.isZero()) {
                details = Res.get("funds.tx.noFundsFromDispute");
                initialTxConfidenceVisibility = false;
            } else if (withdrawalFromBSQWallet) {
                details = Res.get("funds.tx.withdrawnFromBSQWallet");
            } else if (!txFeeForBsqPayment) {
                details = received ? Res.get("funds.tx.receivedFunds") : Res.get("funds.tx.withdrawnFromWallet");
            } else if (details.isEmpty()) {
                details = Res.get("funds.tx.txFeePaymentForBsqTx");
            }
        }
        // Use tx.getIncludedInBestChainAt() when available, otherwise use tx.getUpdateTime()
        date = transaction.getIncludedInBestChainAt() != null ? transaction.getIncludedInBestChainAt() : transaction.getUpdateTime();
        dateString = DisplayUtils.formatDateTime(date);

        isDustAttackTx = received && valueSentToMe.value < ignoreDustThreshold;
        if (isDustAttackTx) {
            details = Res.get("funds.tx.dustAttackTx");
        }

        // confidence
        lazyFieldsSupplier = Suppliers.memoize(() -> new LazyFields() {{
            txConfidenceIndicator = new TxConfidenceIndicator();
            txConfidenceIndicator.setId("funds-confidence");
            tooltip = new Tooltip(Res.get("shared.notUsedYet"));
            txConfidenceIndicator.setProgress(0);
            txConfidenceIndicator.setTooltip(tooltip);
            txConfidenceIndicator.setVisible(initialTxConfidenceVisibility);

            TransactionConfidence confidence = transaction.getConfidence();
            GUIUtil.updateConfidence(confidence, tooltip, txConfidenceIndicator);
            confirmations = confidence.getDepthInBlocks();
        }});
    }


    public TxConfidenceIndicator getTxConfidenceIndicator() {
        return lazy().txConfidenceIndicator;
    }

    public final String getDateString() {
        return dateString;
    }


    public String getAmount() {
        return formatter.formatCoin(amountAsCoin);
    }

    public Coin getAmountAsCoin() {
        return amountAsCoin;
    }


    public String getAddressString() {
        return addressString;
    }

    public String getDirection() {
        return direction;
    }

    public String getTxId() {
        return txId;
    }

    public boolean getReceived() {
        return received;
    }

    public String getDetails() {
        return details;
    }

    public Date getDate() {
        return date;
    }

    @Nullable
    public Tradable getTradable() {
        return tradable;
    }

    public String getNumConfirmations() {
        return String.valueOf(lazy().confirmations);
    }

    public String getMemo() {
        return memo;
    }

    @Override
    public boolean match(String filterString) {
        if (filterString.isEmpty()) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getTxId(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getDetails(), filterString)) {
            return true;
        }
        if (getMemo() != null && StringUtils.containsIgnoreCase(getMemo(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getDirection(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getDateString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getAmount(), filterString)) {
            return true;
        }
        return StringUtils.containsIgnoreCase(getAddressString(), filterString);
    }
}
