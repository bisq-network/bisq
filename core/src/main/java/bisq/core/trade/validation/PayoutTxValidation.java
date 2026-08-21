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
import bisq.core.btc.wallet.utils.PayoutTransactionUtil;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.Arrays;

import static bisq.core.trade.validation.TransactionValidation.checkDerEncodedEcdsaSignature;
import static bisq.core.trade.validation.TransactionValidation.checkMultiSigPubKey;
import static bisq.core.trade.validation.TransactionValidation.checkTransaction;
import static bisq.core.trade.validation.TransactionValidation.toVerifiedTransaction;
import static bisq.core.util.Validator.checkIsNotNegative;
import static bisq.core.util.Validator.checkNonBlankString;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class PayoutTxValidation {
    private PayoutTxValidation() {
    }


    /* --------------------------------------------------------------------- */
    // Payout transaction
    /* --------------------------------------------------------------------- */

    public static byte[] checkPayoutTx(byte[] serializedPayoutTx,
                                       BtcWalletService btcWalletService,
                                       Transaction depositTx,
                                       Coin buyerPayoutAmount,
                                       Coin sellerPayoutAmount,
                                       String buyerPayoutAddressString,
                                       String sellerPayoutAddressString,
                                       byte[] buyerMultiSigPubKey,
                                       byte[] sellerMultiSigPubKey) {
        byte[] checkedSerializedPayoutTx = checkNonEmptyBytes(serializedPayoutTx, "serializedPayoutTx");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        NetworkParameters params = checkNotNull(btcWalletService.getParams(),
                "btcWalletService.getParams() must not be null");

        Transaction verifiedPayoutTx = toVerifiedTransaction(checkedSerializedPayoutTx, btcWalletService);
        checkPayoutTx(verifiedPayoutTx,
                depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString,
                buyerMultiSigPubKey,
                sellerMultiSigPubKey,
                params);
        return checkedSerializedPayoutTx;
    }

    public static Transaction checkPayoutTx(Transaction payoutTx,
                                            Transaction depositTx,
                                            Coin buyerPayoutAmount,
                                            Coin sellerPayoutAmount,
                                            String buyerPayoutAddressString,
                                            String sellerPayoutAddressString,
                                            byte[] buyerMultiSigPubKey,
                                            byte[] sellerMultiSigPubKey,
                                            NetworkParameters params) {
        Transaction checkedPayoutTx = checkNotNull(payoutTx, "payoutTx must not be null");
        Transaction checkedDepositTx = checkNotNull(depositTx, "depositTx must not be null");
        Coin checkedBuyerPayoutAmount = checkIsNotNegative(buyerPayoutAmount, "buyerPayoutAmount");
        Coin checkedSellerPayoutAmount = checkIsNotNegative(sellerPayoutAmount, "sellerPayoutAmount");
        String checkedBuyerPayoutAddressString = checkNonBlankString(buyerPayoutAddressString,
                "buyerPayoutAddressString");
        String checkedSellerPayoutAddressString = checkNonBlankString(sellerPayoutAddressString,
                "sellerPayoutAddressString");
        byte[] checkedBuyerMultiSigPubKey = checkMultiSigPubKey(buyerMultiSigPubKey);
        byte[] checkedSellerMultiSigPubKey = checkMultiSigPubKey(sellerMultiSigPubKey);
        NetworkParameters checkedParams = checkNotNull(params, "params must not be null");

        checkArgument(checkedBuyerPayoutAmount.isPositive() || checkedSellerPayoutAmount.isPositive(),
                "At least one payout amount must be positive");

        checkTransaction(checkedPayoutTx);

        Script redeemScript = PayoutTransactionUtil.get2of2MultiSigRedeemScript(checkedBuyerMultiSigPubKey, checkedSellerMultiSigPubKey);
        PayoutTxValidationUtils.checkPayoutTxOutputSumNotGreaterThanDepositOutputValue(checkedDepositTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                "payoutTx");
        PayoutTxValidationUtils.checkPayoutTxInputSpendsDepositOutputZero(checkedPayoutTx,
                checkedDepositTx,
                "payoutTx");
        PayoutTxValidationUtils.checkPayoutTxOutputAmountsAndAddresses(checkedPayoutTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                checkedBuyerPayoutAddressString,
                checkedSellerPayoutAddressString,
                checkedParams,
                "payoutTx",
                "At least one payout amount must be positive");
        TransactionOutput depositOutput = PayoutTxValidationUtils.getDepositOutputZero(checkedDepositTx);
        checkPayoutTxInputScript(checkedPayoutTx,
                depositOutput,
                redeemScript,
                checkedBuyerMultiSigPubKey,
                checkedSellerMultiSigPubKey);

        return checkedPayoutTx;
    }


    /* --------------------------------------------------------------------- */
    // Payout transaction signature
    /* --------------------------------------------------------------------- */

    public static String checkTradePayoutAddressEntry(String contractPayoutAddress,
                                                      BtcWalletService btcWalletService,
                                                      String tradeId,
                                                      String roleName) {
        String checkedContractPayoutAddress = checkNonBlankString(contractPayoutAddress, "contractPayoutAddress");
        NetworkParameters params = checkNotNull(checkNotNull(btcWalletService, "btcWalletService must not be null").getParams(),
                "btcWalletService.getParams() must not be null");
        AddressEntry payoutAddressEntry = checkNotNull(
                btcWalletService.getAddressEntry(tradeId, AddressEntry.Context.TRADE_PAYOUT).orElse(null),
                "%s payout address entry must exist. tradeId=%s, contractAddress=%s",
                roleName,
                tradeId,
                checkedContractPayoutAddress);
        Address contractAddress = Address.fromString(params, checkedContractPayoutAddress);
        Address addressEntryAddress = Address.fromString(params,
                checkNonBlankString(payoutAddressEntry.getAddressString(), "payoutAddressEntry.getAddressString()"));
        checkArgument(contractAddress.equals(addressEntryAddress),
                "%s payout address from AddressEntry must match the contract. " +
                        "tradeId=%s, contractAddress=%s, addressEntry=%s",
                roleName, tradeId, checkedContractPayoutAddress, payoutAddressEntry);
        return checkedContractPayoutAddress;
    }

    public static String checkTradingPeerPayoutAddress(String contractPayoutAddress,
                                                       String tradingPeerPayoutAddress,
                                                       BtcWalletService btcWalletService,
                                                       String tradeId,
                                                       String roleName) {
        String checkedContractPayoutAddress = checkNonBlankString(contractPayoutAddress, "contractPayoutAddress");
        String checkedTradingPeerPayoutAddress = checkNonBlankString(tradingPeerPayoutAddress,
                "tradingPeerPayoutAddress");
        NetworkParameters params = checkNotNull(checkNotNull(btcWalletService, "btcWalletService must not be null").getParams(),
                "btcWalletService.getParams() must not be null");
        Address contractAddress = Address.fromString(params, checkedContractPayoutAddress);
        Address tradingPeerAddress = Address.fromString(params, checkedTradingPeerPayoutAddress);
        checkArgument(contractAddress.equals(tradingPeerAddress),
                "%s payout address from TradingPeer must match the contract. " +
                        "tradeId=%s, contractAddress=%s, tradingPeerAddress=%s",
                roleName, tradeId, checkedContractPayoutAddress, checkedTradingPeerPayoutAddress);
        return checkedContractPayoutAddress;
    }

    public static byte[] checkBuyersPayoutTxSignature(byte[] buyerSignature,
                                                      String buyerPayoutAddress,
                                                      Trade trade,
                                                      byte[] buyerMultiSigPubKey,
                                                      byte[] sellerMultiSigPubKey,
                                                      BtcWalletService btcWalletService) {
        Contract contract = checkNotNull(trade.getContract(), "trade.getContract() must not be null");
        String buyerPayoutAddressFromContract = contract.getBuyerPayoutAddressString();
        NetworkParameters params = checkNotNull(checkNotNull(btcWalletService, "btcWalletService must not be null").getParams(),
                "btcWalletService.getParams() must not be null");
        Address contractBuyerPayoutAddress = Address.fromString(params,
                checkNonBlankString(buyerPayoutAddressFromContract, "buyerPayoutAddressFromContract"));
        Address buyerPayoutAddressFromMessage = Address.fromString(params,
                checkNonBlankString(buyerPayoutAddress, "buyerPayoutAddress"));
        checkArgument(contractBuyerPayoutAddress.equals(buyerPayoutAddressFromMessage),
                "Buyer payout address must match the contract. tradeId=%s, contractAddress=%s, buyerPayoutAddress=%s",
                trade.getId(), buyerPayoutAddressFromContract, buyerPayoutAddress);
        String sellerPayoutAddress = checkTradePayoutAddressEntry(contract.getSellerPayoutAddressString(),
                btcWalletService,
                trade.getId(),
                "Seller");
        Offer offer = checkNotNull(trade.getOffer(), "trade.getOffer() must not be null");
        Transaction depositTx = checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
        Coin tradeAmount = checkNotNull(trade.getAmount(), "trade.getAmount() must not be null");
        Coin buyerSecurityDeposit = checkNotNull(offer.getBuyerSecurityDeposit(), "offer.getBuyerSecurityDeposit() must not be null");
        Coin buyerPayoutAmount = tradeAmount.add(buyerSecurityDeposit);
        @SuppressWarnings("UnnecessaryLocalVariable")
        Coin sellerSecurityDeposit = checkNotNull(offer.getSellerSecurityDeposit(), "offer.getSellerSecurityDeposit() must not be null");
        Coin sellerPayoutAmount = sellerSecurityDeposit;
        return checkPayoutTxSignature(buyerSignature,
                btcWalletService,
                depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddress,
                sellerPayoutAddress,
                buyerMultiSigPubKey,
                buyerMultiSigPubKey,
                sellerMultiSigPubKey,
                "buyerSignature");
    }

    public static byte[] checkPayoutTxSignature(byte[] txSignature,
                                                BtcWalletService btcWalletService,
                                                Transaction depositTx,
                                                Coin buyerPayoutAmount,
                                                Coin sellerPayoutAmount,
                                                String buyerPayoutAddressString,
                                                String sellerPayoutAddressString,
                                                byte[] signingMultiSigPubKey,
                                                byte[] buyerMultiSigPubKey,
                                                byte[] sellerMultiSigPubKey,
                                                String signatureName) {
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        NetworkParameters params = checkNotNull(btcWalletService.getParams(),
                "btcWalletService.getParams() must not be null");
        return checkPayoutTxSignature(txSignature,
                depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString,
                signingMultiSigPubKey,
                buyerMultiSigPubKey,
                sellerMultiSigPubKey,
                params,
                signatureName);
    }

    public static byte[] checkPayoutTxSignature(byte[] txSignature,
                                                Transaction depositTx,
                                                Coin buyerPayoutAmount,
                                                Coin sellerPayoutAmount,
                                                String buyerPayoutAddressString,
                                                String sellerPayoutAddressString,
                                                byte[] signingMultiSigPubKey,
                                                byte[] buyerMultiSigPubKey,
                                                byte[] sellerMultiSigPubKey,
                                                NetworkParameters params,
                                                String signatureName) {
        String checkedSignatureName = checkNonBlankString(signatureName, "signatureName");
        byte[] checkedTxSignature = checkDerEncodedEcdsaSignature(txSignature);
        Transaction checkedDepositTx = checkNotNull(depositTx, "depositTx must not be null");
        Coin checkedBuyerPayoutAmount = checkIsNotNegative(buyerPayoutAmount, "buyerPayoutAmount");
        Coin checkedSellerPayoutAmount = checkIsNotNegative(sellerPayoutAmount, "sellerPayoutAmount");
        String checkedBuyerPayoutAddressString = checkNonBlankString(buyerPayoutAddressString,
                "buyerPayoutAddressString");
        String checkedSellerPayoutAddressString = checkNonBlankString(sellerPayoutAddressString,
                "sellerPayoutAddressString");
        byte[] checkedSigningMultiSigPubKey = checkMultiSigPubKey(signingMultiSigPubKey);
        byte[] checkedBuyerMultiSigPubKey = checkMultiSigPubKey(buyerMultiSigPubKey);
        byte[] checkedSellerMultiSigPubKey = checkMultiSigPubKey(sellerMultiSigPubKey);
        NetworkParameters checkedParams = checkNotNull(params, "params must not be null");

        checkArgument(Arrays.equals(checkedSigningMultiSigPubKey, checkedBuyerMultiSigPubKey) ||
                        Arrays.equals(checkedSigningMultiSigPubKey, checkedSellerMultiSigPubKey),
                "%s signer pubkey must be one of the payout tx multisig pubkeys",
                checkedSignatureName);

        Transaction payoutTx = createUnsignedPayoutTx(checkedDepositTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                checkedBuyerPayoutAddressString,
                checkedSellerPayoutAddressString,
                checkedParams);
        PayoutTxValidationUtils.checkPayoutTxOutputSumNotGreaterThanDepositOutputValue(checkedDepositTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                "payoutTx");
        PayoutTxValidationUtils.checkPayoutTxInputSpendsDepositOutputZero(payoutTx,
                checkedDepositTx,
                "payoutTx");
        PayoutTxValidationUtils.checkPayoutTxOutputAmountsAndAddresses(payoutTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                checkedBuyerPayoutAddressString,
                checkedSellerPayoutAddressString,
                checkedParams,
                "payoutTx",
                "At least one payout amount must be positive");

        TransactionOutput depositOutput = PayoutTxValidationUtils.getDepositOutputZero(checkedDepositTx);
        Script redeemScript = PayoutTransactionUtil.get2of2MultiSigRedeemScript(checkedBuyerMultiSigPubKey, checkedSellerMultiSigPubKey);
        boolean isExpectedP2wshOutput = Arrays.equals(depositOutput.getScriptPubKey().getProgram(),
                ScriptBuilder.createP2WSHOutputScript(redeemScript).getProgram());
        checkArgument(isExpectedP2wshOutput,
                "depositTx output 0 must be the expected P2WSH 2-of-2 multisig script");

        Sha256Hash sigHash = payoutTx.hashForWitnessSignature(0,
                redeemScript,
                depositOutput.getValue(),
                Transaction.SigHash.ALL,
                false);
        try {
            ECKey.ECDSASignature ecdsaSignature = ECKey.ECDSASignature.decodeFromDER(checkedTxSignature);
            ECKey signingKey = ECKey.fromPublicOnly(checkedSigningMultiSigPubKey);
            checkArgument(signingKey.verify(sigHash, ecdsaSignature),
                    "%s is not valid for the expected payout tx",
                    checkedSignatureName);
        } catch (SignatureDecodeException e) {
            throw new IllegalArgumentException("Invalid " + checkedSignatureName, e);
        }

        return checkedTxSignature;
    }


    /* --------------------------------------------------------------------- */
    // Payout transaction input script
    /* --------------------------------------------------------------------- */

    private static void checkPayoutTxInputScript(Transaction payoutTx,
                                                 TransactionOutput depositOutput,
                                                 Script redeemScript,
                                                 byte[] buyerMultiSigPubKey,
                                                 byte[] sellerMultiSigPubKey) {
        Script depositOutputScript = depositOutput.getScriptPubKey();
        boolean isSegwitPayout = Arrays.equals(depositOutputScript.getProgram(),
                ScriptBuilder.createP2WSHOutputScript(redeemScript).getProgram());
        checkArgument(isSegwitPayout,
                "depositTx output 0 must be the expected P2WSH 2-of-2 multisig script");

        try {
            TransactionInput input = payoutTx.getInput(0);
            input.getScriptSig().correctlySpends(payoutTx,
                    0,
                    input.getWitness(),
                    depositOutput.getValue(),
                    depositOutput.getScriptPubKey(),
                    Script.ALL_VERIFY_FLAGS);
        } catch (Throwable t) {
            throw new IllegalArgumentException("payoutTx input script does not spend depositTx output 0", t);
        }

        checkP2wshSignatures(payoutTx, depositOutput, redeemScript, buyerMultiSigPubKey, sellerMultiSigPubKey);
    }

    private static void checkP2wshSignatures(Transaction payoutTx,
                                             TransactionOutput depositOutput,
                                             Script redeemScript,
                                             byte[] buyerMultiSigPubKey,
                                             byte[] sellerMultiSigPubKey) {
        TransactionWitness witness = payoutTx.getInput(0).getWitness();
        checkArgument(!TransactionWitness.EMPTY.equals(witness), "payoutTx input witness must not be empty");

        try {
            checkArgument(witness.getPush(0).length == 0, "payoutTx witness dummy element must be empty");
            byte[] sellerSignatureBytes = witness.getPush(1);
            byte[] buyerSignatureBytes = witness.getPush(2);
            byte[] witnessRedeemScript = witness.getPush(3);
            checkArgument(Arrays.equals(witnessRedeemScript, redeemScript.getProgram()),
                    "payoutTx witness redeem script does not match the expected 2-of-2 multisig script");

            TransactionSignature sellerSignature = TransactionSignature.decodeFromBitcoin(sellerSignatureBytes,
                    true,
                    true);
            TransactionSignature buyerSignature = TransactionSignature.decodeFromBitcoin(buyerSignatureBytes,
                    true,
                    true);
            checkArgument(sellerSignature.sigHashMode() == Transaction.SigHash.ALL && !sellerSignature.anyoneCanPay(),
                    "seller payoutTx witness signature must use SIGHASH_ALL");
            checkArgument(buyerSignature.sigHashMode() == Transaction.SigHash.ALL && !buyerSignature.anyoneCanPay(),
                    "buyer payoutTx witness signature must use SIGHASH_ALL");
            Sha256Hash sigHash = payoutTx.hashForWitnessSignature(0,
                    redeemScript,
                    depositOutput.getValue(),
                    Transaction.SigHash.ALL,
                    false);
            ECKey buyerKey = ECKey.fromPublicOnly(buyerMultiSigPubKey);
            ECKey sellerKey = ECKey.fromPublicOnly(sellerMultiSigPubKey);
            checkArgument(buyerKey.verify(sigHash, buyerSignature) && sellerKey.verify(sigHash, sellerSignature),
                    "payoutTx witness signatures are invalid");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage();
            throw new IllegalArgumentException(
                    message == null || message.isEmpty()
                            ? "payoutTx witness signatures are invalid"
                            : "payoutTx witness signatures are invalid: " + message,
                    e);
        }
    }

    private static Transaction createUnsignedPayoutTx(Transaction depositTx,
                                                      Coin buyerPayoutAmount,
                                                      Coin sellerPayoutAmount,
                                                      String buyerPayoutAddressString,
                                                      String sellerPayoutAddressString,
                                                      NetworkParameters params) {
        TransactionOutput depositOutput = PayoutTxValidationUtils.getDepositOutputZero(depositTx);
        Transaction transaction = new Transaction(params);
        transaction.addInput(depositOutput);
        if (buyerPayoutAmount.isPositive()) {
            transaction.addOutput(buyerPayoutAmount, Address.fromString(params, buyerPayoutAddressString));
        }
        if (sellerPayoutAmount.isPositive()) {
            transaction.addOutput(sellerPayoutAmount, Address.fromString(params, sellerPayoutAddressString));
        }
        checkArgument(!transaction.getOutputs().isEmpty(), "payoutTx must have at least one output");
        return transaction;
    }
}
