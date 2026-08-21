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

package bisq.core.trade.validation;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

import com.google.common.annotations.VisibleForTesting;

import static bisq.core.trade.validation.TransactionValidation.checkBitcoinAddress;
import static bisq.core.trade.validation.TransactionValidation.checkTransaction;
import static bisq.core.util.Validator.checkIsNotNegative;
import static bisq.core.util.Validator.checkIsPositive;
import static bisq.core.util.Validator.checkNonBlankString;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class MediatedPayoutTxValidation {
    private MediatedPayoutTxValidation() {
    }


    /* --------------------------------------------------------------------- */
    // Mediated payout amounts
    /* --------------------------------------------------------------------- */

    public static void checkMediatedPayoutAmounts(Coin buyerPayoutAmount,
                                                  Coin sellerPayoutAmount,
                                                  Coin expectedTotalPayoutAmount) {
        Coin checkedBuyerPayoutAmount = checkIsNotNegative(buyerPayoutAmount, "buyerPayoutAmount");
        Coin checkedSellerPayoutAmount = checkIsNotNegative(sellerPayoutAmount, "sellerPayoutAmount");
        Coin checkedExpectedTotalPayoutAmount = checkIsPositive(expectedTotalPayoutAmount, "expectedTotalPayoutAmount");

        Coin sum = checkedBuyerPayoutAmount.add(checkedSellerPayoutAmount);
        checkArgument(checkedExpectedTotalPayoutAmount.equals(sum),
                "Payout amount does not match buyerPayoutAmount=%s; sellerPayoutAmount=%s; expectedTotalPayoutAmount=%s",
                checkedBuyerPayoutAmount.toFriendlyString(),
                checkedSellerPayoutAmount.toFriendlyString(),
                checkedExpectedTotalPayoutAmount.toFriendlyString());
    }


    /* --------------------------------------------------------------------- */
    // Mediated payout addresses
    /* --------------------------------------------------------------------- */

    public static void checkMediatedPayoutAddresses(String buyerPayoutAddressString,
                                                    Coin buyerPayoutAmount,
                                                    String sellerPayoutAddressString,
                                                    Coin sellerPayoutAmount,
                                                    BtcWalletService btcWalletService) {
        String checkedBuyerPayoutAddressString = checkNonBlankString(buyerPayoutAddressString, "buyerPayoutAddressString");
        Coin checkedBuyerPayoutAmount = checkIsNotNegative(buyerPayoutAmount, "buyerPayoutAmount");
        String checkedSellerPayoutAddressString = checkNonBlankString(sellerPayoutAddressString, "sellerPayoutAddressString");
        Coin checkedSellerPayoutAmount = checkIsNotNegative(sellerPayoutAmount, "sellerPayoutAmount");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        if (checkedBuyerPayoutAmount.isPositive()) {
            checkBitcoinAddress(checkedBuyerPayoutAddressString, btcWalletService);
        }
        if (checkedSellerPayoutAmount.isPositive()) {
            checkBitcoinAddress(checkedSellerPayoutAddressString, btcWalletService);
        }
    }


    /* --------------------------------------------------------------------- */
    // Mediated payout transaction
    /* --------------------------------------------------------------------- */

    public static Transaction checkMediatedPayoutTx(Transaction payoutTx,
                                                    Trade trade,
                                                    BtcWalletService btcWalletService) {
        checkNotNull(payoutTx, "payoutTx must not be null");
        checkNotNull(trade, "trade must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        ProcessModel processModel = checkNotNull(trade.getProcessModel(), "processModel must not be null");
        Contract contract = checkNotNull(trade.getContract(), "contract must not be null");
        TradingPeer tradingPeer = checkNotNull(processModel.getTradePeer(), "tradingPeer must not be null");
        Transaction depositTx = checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
        NetworkParameters params = checkNotNull(btcWalletService.getParams(), "params must not be null");

        Coin buyerPayoutAmount = Coin.valueOf(processModel.getBuyerPayoutAmountFromMediation());
        Coin sellerPayoutAmount = Coin.valueOf(processModel.getSellerPayoutAmountFromMediation());
        Coin expectedTotalPayoutAmount = getExpectedTotalPayoutAmount(trade);
        checkMediatedPayoutAmounts(buyerPayoutAmount, sellerPayoutAmount, expectedTotalPayoutAmount);

        boolean isMyRoleBuyer = contract.isMyRoleBuyer(processModel.getPubKeyRing());
        String myPayoutAddressString = btcWalletService.getOrCreateAddressEntry(trade.getId(),
                AddressEntry.Context.TRADE_PAYOUT).getAddressString();
        String peersPayoutAddressString = tradingPeer.getPayoutAddressString();
        String buyerPayoutAddressString = isMyRoleBuyer ? myPayoutAddressString : peersPayoutAddressString;
        String sellerPayoutAddressString = isMyRoleBuyer ? peersPayoutAddressString : myPayoutAddressString;
        checkMediatedPayoutAddresses(buyerPayoutAddressString,
                buyerPayoutAmount,
                sellerPayoutAddressString,
                sellerPayoutAmount,
                btcWalletService);

        return checkMediatedPayoutTx(payoutTx,
                depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString,
                params);
    }

    public static Transaction checkMediatedPayoutTx(Transaction payoutTx,
                                                    Transaction depositTx,
                                                    Coin buyerPayoutAmount,
                                                    Coin sellerPayoutAmount,
                                                    String buyerPayoutAddressString,
                                                    String sellerPayoutAddressString,
                                                    BtcWalletService btcWalletService) {
        checkNotNull(payoutTx, "payoutTx must not be null");
        checkNotNull(depositTx, "depositTx must not be null");
        Coin checkedBuyerPayoutAmount = checkIsNotNegative(buyerPayoutAmount, "buyerPayoutAmount");
        Coin checkedSellerPayoutAmount = checkIsNotNegative(sellerPayoutAmount, "sellerPayoutAmount");
        String checkedBuyerPayoutAddressString = checkNonBlankString(buyerPayoutAddressString, "buyerPayoutAddressString");
        String checkedSellerPayoutAddressString = checkNonBlankString(sellerPayoutAddressString, "sellerPayoutAddressString");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        NetworkParameters params = checkNotNull(btcWalletService.getParams(),
                "btcWalletService.getParams() must not be null");
        return checkMediatedPayoutTx(payoutTx,
                depositTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                checkedBuyerPayoutAddressString,
                checkedSellerPayoutAddressString,
                params);
    }

    @VisibleForTesting
    static Transaction checkMediatedPayoutTx(Transaction payoutTx,
                                             Transaction depositTx,
                                             Coin buyerPayoutAmount,
                                             Coin sellerPayoutAmount,
                                             String buyerPayoutAddressString,
                                             String sellerPayoutAddressString,
                                             NetworkParameters params) {
        NetworkParameters checkedParams = checkNotNull(params, "params must not be null");
        Transaction checkedPayoutTx = checkTransaction(payoutTx);
        Transaction checkedDepositTx = checkNotNull(depositTx, "depositTx must not be null");
        Coin checkedBuyerPayoutAmount = checkIsNotNegative(buyerPayoutAmount, "buyerPayoutAmount");
        Coin checkedSellerPayoutAmount = checkIsNotNegative(sellerPayoutAmount, "sellerPayoutAmount");

        PayoutTxValidationUtils.checkPayoutTxInputSpendsDepositOutputZero(checkedPayoutTx,
                checkedDepositTx,
                "Mediated payout tx");
        PayoutTxValidationUtils.checkPayoutTxOutputSumNotGreaterThanDepositOutputValue(checkedDepositTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                "Mediated payout tx");
        PayoutTxValidationUtils.checkPayoutTxOutputAmountsAndAddresses(checkedPayoutTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString,
                checkedParams,
                "Mediated payout tx",
                "Mediated payout tx must have at least one positive payout amount");
        return checkedPayoutTx;
    }

    public static byte[] checkPeerMediatedPayoutTxSignature(byte[] txSignature,
                                                            Trade trade,
                                                            BtcWalletService btcWalletService) {
        checkNotNull(trade, "trade must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        ProcessModel processModel = checkNotNull(trade.getProcessModel(), "processModel must not be null");
        Contract contract = checkNotNull(trade.getContract(), "contract must not be null");
        TradingPeer tradingPeer = checkNotNull(processModel.getTradePeer(), "tradingPeer must not be null");
        Transaction depositTx = checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");

        Coin buyerPayoutAmount = Coin.valueOf(processModel.getBuyerPayoutAmountFromMediation());
        Coin sellerPayoutAmount = Coin.valueOf(processModel.getSellerPayoutAmountFromMediation());
        Coin expectedTotalPayoutAmount = getExpectedTotalPayoutAmount(trade);
        checkMediatedPayoutAmounts(buyerPayoutAmount, sellerPayoutAmount, expectedTotalPayoutAmount);

        boolean isMyRoleBuyer = contract.isMyRoleBuyer(processModel.getPubKeyRing());
        String myPayoutAddressString = btcWalletService.getOrCreateAddressEntry(trade.getId(),
                AddressEntry.Context.TRADE_PAYOUT).getAddressString();
        String peersPayoutAddressString = tradingPeer.getPayoutAddressString();
        String buyerPayoutAddressString = isMyRoleBuyer ? myPayoutAddressString : peersPayoutAddressString;
        String sellerPayoutAddressString = isMyRoleBuyer ? peersPayoutAddressString : myPayoutAddressString;
        checkMediatedPayoutAddresses(buyerPayoutAddressString,
                buyerPayoutAmount,
                sellerPayoutAddressString,
                sellerPayoutAmount,
                btcWalletService);

        byte[] myMultiSigPubKey = processModel.getMyMultiSigPubKey();
        byte[] peersMultiSigPubKey = tradingPeer.getMultiSigPubKey();
        byte[] buyerMultiSigPubKey = isMyRoleBuyer ? myMultiSigPubKey : peersMultiSigPubKey;
        byte[] sellerMultiSigPubKey = isMyRoleBuyer ? peersMultiSigPubKey : myMultiSigPubKey;

        return PayoutTxValidation.checkPayoutTxSignature(txSignature,
                btcWalletService,
                depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString,
                peersMultiSigPubKey,
                buyerMultiSigPubKey,
                sellerMultiSigPubKey,
                "mediatedPayoutTxSignature");
    }


    /* --------------------------------------------------------------------- */
    // Trade-derived payout amount
    /* --------------------------------------------------------------------- */

    private static Coin getExpectedTotalPayoutAmount(Trade trade) {
        Offer offer = checkNotNull(trade.getOffer(), "offer must not be null");
        Coin tradeAmount = checkNotNull(trade.getAmount(), "tradeAmount must not be null");
        Coin buyerSecurityDeposit = checkNotNull(offer.getBuyerSecurityDeposit(),
                "offer.getBuyerSecurityDeposit() must not be null");
        Coin sellerSecurityDeposit = checkNotNull(offer.getSellerSecurityDeposit(),
                "offer.getSellerSecurityDeposit() must not be null");
        return tradeAmount
                .add(buyerSecurityDeposit)
                .add(sellerSecurityDeposit);
    }
}
