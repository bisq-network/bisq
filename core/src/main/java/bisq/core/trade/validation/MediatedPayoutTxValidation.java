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
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public static Coin checkMediatedPayoutAmounts(Coin buyerPayoutAmount,
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
        return checkedBuyerPayoutAmount;
    }

    public static String checkMediatedPayoutAddresses(String buyerPayoutAddressString,
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
        return checkedBuyerPayoutAddressString;
    }

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
        Coin validatedBuyerPayoutAmount = checkMediatedPayoutAmounts(buyerPayoutAmount,
                sellerPayoutAmount,
                expectedTotalPayoutAmount);

        boolean isMyRoleBuyer = contract.isMyRoleBuyer(processModel.getPubKeyRing());
        String myPayoutAddressString = btcWalletService.getOrCreateAddressEntry(trade.getId(),
                AddressEntry.Context.TRADE_PAYOUT).getAddressString();
        String peersPayoutAddressString = tradingPeer.getPayoutAddressString();
        String buyerPayoutAddressString = isMyRoleBuyer ? myPayoutAddressString : peersPayoutAddressString;
        String sellerPayoutAddressString = isMyRoleBuyer ? peersPayoutAddressString : myPayoutAddressString;
        String validatedBuyerPayoutAddressString = checkMediatedPayoutAddresses(buyerPayoutAddressString,
                validatedBuyerPayoutAmount,
                sellerPayoutAddressString,
                sellerPayoutAmount,
                btcWalletService);

        return checkMediatedPayoutTx(payoutTx,
                depositTx,
                validatedBuyerPayoutAmount,
                sellerPayoutAmount,
                validatedBuyerPayoutAddressString,
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
        return checkMediatedPayoutTx(payoutTx,
                depositTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                checkedBuyerPayoutAddressString,
                checkedSellerPayoutAddressString,
                btcWalletService.getParams());
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

        checkMediatedPayoutTxInput(checkedPayoutTx, checkedDepositTx);
        checkMediatedPayoutTxOutputSum(checkedDepositTx, checkedBuyerPayoutAmount, checkedSellerPayoutAmount);
        checkMediatedPayoutTxOutputAmountsAndAddresses(checkedPayoutTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString,
                checkedParams);
        return checkedPayoutTx;
    }

    private static Transaction checkMediatedPayoutTxInput(Transaction payoutTx,
                                                          Transaction depositTx) {
        checkNotNull(payoutTx, "payoutTx must not be null");
        checkNotNull(depositTx, "depositTx must not be null");
        checkArgument(payoutTx.getInputs().size() == 1,
                "Number of mediated payout tx inputs must be 1");

        TransactionInput input = payoutTx.getInput(0);
        TransactionOutPoint outpoint = input.getOutpoint();
        checkArgument(depositTx.getTxId().equals(outpoint.getHash()) && outpoint.getIndex() == 0,
                "Input of mediated payout transaction does not point to output 0 of deposit tx. " +
                        "payoutTxId=%s, depositTxId=%s, outpointHash=%s, outpointIndex=%s",
                payoutTx.getTxId(),
                depositTx.getTxId(),
                outpoint.getHash(),
                outpoint.getIndex());
        return payoutTx;
    }

    private static Coin checkMediatedPayoutTxOutputSum(Transaction depositTx,
                                                       Coin buyerPayoutAmount,
                                                       Coin sellerPayoutAmount) {
        Transaction checkedDepositTx = checkNotNull(depositTx, "depositTx must not be null");
        Coin checkedBuyerPayoutAmount = checkIsNotNegative(buyerPayoutAmount, "buyerPayoutAmount");
        Coin checkedSellerPayoutAmount = checkIsNotNegative(sellerPayoutAmount, "sellerPayoutAmount");

        checkArgument(!checkedDepositTx.getOutputs().isEmpty(), "depositTx must not be empty");
        Coin depositOutputValue = checkIsPositive(checkedDepositTx.getOutput(0).getValue(),
                "depositTx output 0 value");
        Coin payoutOutputSum = checkedBuyerPayoutAmount.add(checkedSellerPayoutAmount);
        checkArgument(!payoutOutputSum.isGreaterThan(depositOutputValue),
                "Mediated payout tx output sum must not be greater than depositTx output 0 value. " +
                        "payoutOutputSum=%s, depositOutputValue=%s",
                payoutOutputSum.toFriendlyString(),
                depositOutputValue.toFriendlyString());
        return payoutOutputSum;
    }

    private static Transaction checkMediatedPayoutTxOutputAmountsAndAddresses(Transaction payoutTx,
                                                                              Coin buyerPayoutAmount,
                                                                              Coin sellerPayoutAmount,
                                                                              String buyerPayoutAddressString,
                                                                              String sellerPayoutAddressString,
                                                                              NetworkParameters params) {
        checkNotNull(payoutTx, "payoutTx must not be null");
        Coin checkedBuyerPayoutAmount = checkIsNotNegative(buyerPayoutAmount, "buyerPayoutAmount");
        Coin checkedSellerPayoutAmount = checkIsNotNegative(sellerPayoutAmount, "sellerPayoutAmount");
        checkNotNull(params, "params must not be null");

        List<ExpectedOutput> expectedOutputs = new ArrayList<>();
        if (checkedBuyerPayoutAmount.isPositive()) {
            String checkedBuyerPayoutAddressString = checkNonBlankString(buyerPayoutAddressString, "buyerPayoutAddressString");
            expectedOutputs.add(new ExpectedOutput(checkedBuyerPayoutAmount, checkedBuyerPayoutAddressString, "buyer"));
        }
        if (checkedSellerPayoutAmount.isPositive()) {
            String checkedSellerPayoutAddressString = checkNonBlankString(sellerPayoutAddressString, "sellerPayoutAddressString");
            expectedOutputs.add(new ExpectedOutput(checkedSellerPayoutAmount, checkedSellerPayoutAddressString, "seller"));
        }
        checkArgument(!expectedOutputs.isEmpty(), "Mediated payout tx must have at least one positive payout amount");
        checkArgument(payoutTx.getOutputs().size() == expectedOutputs.size(),
                "Number of mediated payout tx outputs does not match mediation result. actual=%s, expected=%s",
                payoutTx.getOutputs().size(),
                expectedOutputs.size());

        for (int i = 0; i < expectedOutputs.size(); i++) {
            TransactionOutput output = payoutTx.getOutput(i);
            ExpectedOutput expectedOutput = expectedOutputs.get(i);
            checkArgument(output.getValue().equals(expectedOutput.amount),
                    "Mediated payout tx %s output amount does not match mediation result. actual=%s, expected=%s",
                    expectedOutput.role,
                    output.getValue().toFriendlyString(),
                    expectedOutput.amount.toFriendlyString());

            String actualAddress = output.getScriptPubKey().getToAddress(params).toString().toLowerCase(Locale.ROOT);
            String expectedOutputAddress = expectedOutput.address.toLowerCase(Locale.ROOT);
            checkArgument(actualAddress.equals(expectedOutputAddress),
                    "Mediated payout tx %s output address does not match mediation result. actual=%s, expected=%s",
                    expectedOutput.role,
                    actualAddress,
                    expectedOutput.address);
        }
        return payoutTx;
    }

    private static Coin getExpectedTotalPayoutAmount(Trade trade) {
        Offer offer = checkNotNull(trade.getOffer(), "offer must not be null");
        Coin tradeAmount = checkNotNull(trade.getAmount(), "tradeAmount must not be null");
        return tradeAmount
                .add(offer.getBuyerSecurityDeposit())
                .add(offer.getSellerSecurityDeposit());
    }

    private static class ExpectedOutput {
        private final Coin amount;
        private final String address;
        private final String role;

        private ExpectedOutput(Coin amount, String address, String role) {
            this.amount = amount;
            this.address = address;
            this.role = role;
        }
    }
}
