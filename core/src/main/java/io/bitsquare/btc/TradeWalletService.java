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

package io.bitsquare.btc;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.btc.data.InputsAndChangeOutput;
import io.bitsquare.btc.data.PreparedDepositTxAndOffererInputs;
import io.bitsquare.btc.data.RawTransactionInput;
import io.bitsquare.btc.exceptions.SigningException;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
import io.bitsquare.user.Preferences;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.internal.util.$Preconditions.checkArgument;
import static com.google.inject.internal.util.$Preconditions.checkState;

// TradeService handles all relevant transactions used in the trade process
/*
    To maintain a consistent tx structure we use that structure: 
    Always buyers in/outputs/keys first then sellers in/outputs/keys the arbitrators outputs/keys.
    
    Deposit tx:
    IN[0] buyer (mandatory) e.g. 0.1 BTC
    IN[...] optional additional buyer inputs (normally never used as we pay from trade fee tx and always have 1 output there)
    IN[...] seller (mandatory) e.g. 1.1001 BTC
    IN[...] optional additional seller inputs (normally never used as we pay from trade fee tx and always have 1 output there)
    OUT[0] Multisig output (include tx fee for payout tx) e.g. 1.2001
    OUT[1] OP_RETURN with hash of contract and 0 BTC amount
    OUT[...] optional buyer change (normally never used as we pay from trade fee tx and always have 1 output there)
    OUT[...] optional seller change (normally never used as we pay from trade fee tx and always have 1 output there)
    FEE tx fee 0.0001 BTC
    
    Payout tx:
    IN[0] Multisig output from deposit Tx (signed by buyer and trader)
    OUT[0] Buyer payout address
    OUT[1] Seller payout address
    
    We use 0 confirmation transactions to make the trade process practical from usability side. 
    There is no risk for double spends as the deposit transaction would become invalid if any preceding transaction would have been double spent.
    If a preceding transaction in the chain will not make it into the same or earlier block as the deposit transaction the deposit transaction 
    will be invalid as well. 
    Though the deposit need 1 confirmation before the buyer starts the Fiat payment.
    
    We have that chain of transactions:
    1. Deposit from external wallet to our trading wallet: Tx0 (0 conf)
    2. Create offer (or take offer) fee payment from Tx0 output: tx1 (0 conf)
    3. Deposit tx created with inputs from tx1 of both traders: Tx2 (here we wait for 1 conf)
    
    Fiat transaction will not start before we get at least 1 confirmation for the deposit tx, then we can proceed.
    4. Payout tx with input from MS output and output to both traders: Tx3 (0 conf)
    5. Withdrawal to external wallet from Tx3: Tx4 (0 conf)
    
    After the payout transaction we also don't have issues with 0 conf or if not both tx (payout, withdrawal) make it into a block. 
    Worst case is to rebroadcast the transactions (TODO: is not implemented yet).
    
 */
public class TradeWalletService {
    private static final Logger log = LoggerFactory.getLogger(TradeWalletService.class);

    private final NetworkParameters params;
    @Nullable
    private Wallet wallet;
    @Nullable
    private WalletAppKit walletAppKit;
    @Nullable
    private KeyParameter aesKey;
    private AddressEntryList addressEntryList;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeWalletService(Preferences preferences) {
        this.params = preferences.getBitcoinNetwork().getParameters();
    }

    // After WalletService is initialized we get the walletAppKit set
    public void setWalletAppKit(WalletAppKit walletAppKit) {
        this.walletAppKit = walletAppKit;
        wallet = walletAppKit.wallet();
    }

    public void setAesKey(KeyParameter aesKey) {
        this.aesKey = aesKey;
    }

    @Nullable
    public KeyParameter getAesKey() {
        return aesKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade fee
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param reservedForTradeAddress From where we want to spend the transaction fee. Used also as change reservedForTradeAddress.
     * @param useSavingsWallet
     * @param tradingFee              The amount of the trading fee.
     * @param feeReceiverAddresses    The reservedForTradeAddress of the receiver of the trading fee (arbitrator).   @return The broadcasted transaction
     * @throws InsufficientMoneyException
     * @throws AddressFormatException
     */
    public Transaction createTradingFeeTx(Address fundingAddress, Address reservedForTradeAddress, Address changeAddress, Coin reservedFundsForOffer,
                                          boolean useSavingsWallet, Coin tradingFee, String feeReceiverAddresses)
            throws InsufficientMoneyException, AddressFormatException {
        Transaction tradingFeeTx = new Transaction(params);
        Preconditions.checkArgument(Restrictions.isAboveFixedTxFeeForTradesAndDust(tradingFee),
                "You cannot send an amount which are smaller than the fee + dust output.");
        Coin outPutAmount = tradingFee.subtract(FeePolicy.getFixedTxFeeForTrades());
        tradingFeeTx.addOutput(outPutAmount, new Address(params, feeReceiverAddresses));
        // the reserved amount we need for the trade we send to our trade reservedForTradeAddress
        tradingFeeTx.addOutput(reservedFundsForOffer, reservedForTradeAddress);

        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        // In case of double spend we will detect later in the trade process and use a ban score to penalize bad behaviour (not impl. yet)
        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tradingFeeTx);
        sendRequest.shuffleOutputs = false;
        sendRequest.aesKey = aesKey;
        if (useSavingsWallet)
            sendRequest.coinSelector = new SavingsWalletCoinSelector(params, getAddressEntryListAsImmutableList());
        else
            sendRequest.coinSelector = new TradeWalletCoinSelector(params, fundingAddress);
        // We use a fixed fee
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.fee = FeePolicy.getFixedTxFeeForTrades();

        // Change is optional in case of overpay or use of funds from savings wallet
        sendRequest.changeAddress = changeAddress;

        checkNotNull(wallet, "Wallet must not be null");
        wallet.completeTx(sendRequest);
        printTxWithInputs("tradingFeeTx", tradingFeeTx);

        return tradingFeeTx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We construct the deposit transaction in the way that the buyer is always the first entry (inputs, outputs, MS keys) and then the seller.
    // In the creation of the deposit tx the taker/offerer roles are the determining roles instead of buyer/seller. 
    // In the payout tx is is the buyer/seller role. We keep the buyer/seller ordering over all transactions to not get confusion with ordering,
    // which is important to follow correctly specially for the order of the MS keys.


    /**
     * The taker creates a dummy transaction to get the input(s) and optional change output for the amount and the takersAddressEntry for that trade.
     * That will be used to send to the offerer for creating the deposit transaction.
     *
     * @param inputAmount        Amount of takers input
     * @param takersAddressEntry Address entry of taker
     * @return A data container holding the inputs, the output value and address
     * @throws TransactionVerificationException
     * @throws WalletException
     */
    public InputsAndChangeOutput takerCreatesDepositsTxInputs(Coin inputAmount, AddressEntry takersAddressEntry, Address takersChangeAddress) throws
            TransactionVerificationException, WalletException, AddressFormatException {
        log.trace("takerCreatesDepositsTxInputs called");
        log.trace("inputAmount " + inputAmount.toFriendlyString());
        log.trace("takersAddressEntry " + takersAddressEntry.toString());

        // We add the mining fee 2 times to the deposit tx:
        // 1. Will be spent when publishing the deposit tx (paid by buyer)
        // 2. Will be added to the MS amount, so when publishing the payout tx the fee is already there and the outputs are not changed by fee reduction
        // The fee for the payout will be paid by the seller.

        /*
         The tx we create has that structure:
         
         IN[0]  any input > inputAmount (including tx fee) (unsigned)
         IN[1...n] optional inputs supported, but normally there is just 1 input (unsigned)
         OUT[0] dummyOutputAmount (inputAmount - tx fee)
         OUT[1] Optional Change = inputAmount - dummyOutputAmount - tx fee
         
         We are only interested in the inputs and the optional change output.
         */

        // inputAmount includes the tx fee. So we subtract the fee to get the dummyOutputAmount.
        Coin dummyOutputAmount = inputAmount.subtract(FeePolicy.getFixedTxFeeForTrades());

        Transaction dummyTX = new Transaction(params);
        // The output is just used to get the right inputs and change outputs, so we use an anonymous ECKey, as it will never be used for anything.
        // We don't care about fee calculation differences between the real tx and that dummy tx as we use a static tx fee.
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTX, dummyOutputAmount, new ECKey().toAddress(params));
        dummyTX.addOutput(dummyOutput);

        // Find the needed inputs to pay the output, optionally add 1 change output.
        // Normally only 1 input and no change output is used, but we support multiple inputs and 1 change output. 
        // Our spending transaction output is from the create offer fee payment. 
        addAvailableInputsAndChangeOutputs(dummyTX, takersAddressEntry, takersChangeAddress);

        // The completeTx() call signs the input, but we don't want to pass over signed tx inputs so we remove the signature
        removeSignatures(dummyTX);

        verifyTransaction(dummyTX);

        printTxWithInputs("dummyTX", dummyTX);

        List<RawTransactionInput> rawTransactionInputList = dummyTX.getInputs().stream()
                .map(e -> {
                    checkNotNull(e.getConnectedOutput(), "e.getConnectedOutput() must not be null");
                    checkNotNull(e.getConnectedOutput().getParentTransaction(), "e.getConnectedOutput().getParentTransaction() must not be null");
                    checkNotNull(e.getValue(), "e.getValue() must not be null");
                    return getRawInputFromTransactionInput(e);
                })
                .collect(Collectors.toList());

        // We don't support more then 1 change outputs, so there are max. 2 outputs
        checkArgument(dummyTX.getOutputs().size() < 3);
        // Only interested in optional change output, the dummy output at index 0 is ignored (that's why we use index 1)
        TransactionOutput changeOutput = dummyTX.getOutputs().size() == 2 ? dummyTX.getOutputs().get(1) : null;
        long changeOutputValue = 0L;
        String changeOutputAddress = null;
        if (changeOutput != null) {
            changeOutputValue = changeOutput.getValue().getValue();
            Address addressFromP2PKHScript = changeOutput.getAddressFromP2PKHScript(params);
            checkNotNull(addressFromP2PKHScript, "changeOutput.getAddressFromP2PKHScript(params) must not be null");
            changeOutputAddress = addressFromP2PKHScript.toString();
        }

        return new InputsAndChangeOutput(new ArrayList<>(rawTransactionInputList), changeOutputValue, changeOutputAddress);
    }

    /**
     * The offerer creates the deposit transaction using the takers input(s) and optional output and signs his input(s).
     *
     * @param offererIsBuyer            The flag indicating if we are in the offerer as buyer role or the opposite.
     * @param contractHash              The hash of the contract to be added to the OP_RETURN output.
     * @param offererInputAmount        The input amount of the offerer.
     * @param msOutputAmount            The output amount to our MS output.
     * @param takerRawTransactionInputs Raw data for the connected outputs for all inputs of the taker (normally 1 input)
     * @param takerChangeOutputValue    Optional taker change output value
     * @param takerChangeAddressString  Optional taker change address
     * @param offererAddressEntry       The offerers address entry.
     * @param buyerPubKey               The public key of the buyer.
     * @param sellerPubKey              The public key of the seller.
     * @param arbitratorPubKey          The public key of the arbitrator.
     * @return A data container holding the serialized transaction and the offerer raw inputs
     * @throws SigningException
     * @throws TransactionVerificationException
     * @throws WalletException
     */
    public PreparedDepositTxAndOffererInputs offererCreatesAndSignsDepositTx(boolean offererIsBuyer,
                                                                             byte[] contractHash,
                                                                             Coin offererInputAmount,
                                                                             Coin msOutputAmount,
                                                                             List<RawTransactionInput> takerRawTransactionInputs,
                                                                             long takerChangeOutputValue,
                                                                             @Nullable String takerChangeAddressString,
                                                                             AddressEntry offererAddressEntry,
                                                                             Address offererChangeAddress,
                                                                             byte[] buyerPubKey,
                                                                             byte[] sellerPubKey,
                                                                             byte[] arbitratorPubKey)
            throws SigningException, TransactionVerificationException, WalletException, AddressFormatException {
        log.trace("offererCreatesAndSignsDepositTx called");
        log.trace("offererIsBuyer " + offererIsBuyer);
        log.trace("offererInputAmount " + offererInputAmount.toFriendlyString());
        log.trace("msOutputAmount " + msOutputAmount.toFriendlyString());
        log.trace("takerRawInputs " + takerRawTransactionInputs.toString());
        log.trace("takerChangeOutputValue " + takerChangeOutputValue);
        log.trace("takerChangeAddressString " + takerChangeAddressString);
        log.trace("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.trace("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.trace("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        checkArgument(!takerRawTransactionInputs.isEmpty());

        // First we construct a dummy TX to get the inputs and outputs we want to use for the real deposit tx. 
        // Similar to the way we did in the createTakerDepositTxInputs method.
        Transaction dummyTx = new Transaction(params);
        Coin dummyOutputAmount = offererInputAmount.subtract(FeePolicy.getFixedTxFeeForTrades());
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTx, dummyOutputAmount, new ECKey().toAddress(params));
        dummyTx.addOutput(dummyOutput);
        addAvailableInputsAndChangeOutputs(dummyTx, offererAddressEntry, offererChangeAddress);
        // Normally we have only 1 input but we support multiple inputs if the user has paid in with several transactions.
        List<TransactionInput> offererInputs = dummyTx.getInputs();
        TransactionOutput offererOutput = null;

        // We don't support more then 1 optional change output
        Preconditions.checkArgument(dummyTx.getOutputs().size() < 3, "dummyTx.getOutputs().size() >= 3");

        // Only save change outputs, the dummy output is ignored (that's why we start with index 1)
        if (dummyTx.getOutputs().size() > 1)
            offererOutput = dummyTx.getOutput(1);

        // Now we construct the real deposit tx
        Transaction preparedDepositTx = new Transaction(params);

        ArrayList<RawTransactionInput> offererRawTransactionInputs = new ArrayList<>();
        if (offererIsBuyer) {
            // Add buyer inputs
            for (TransactionInput input : offererInputs) {
                preparedDepositTx.addInput(input);
                offererRawTransactionInputs.add(getRawInputFromTransactionInput(input));
            }

            // Add seller inputs
            // the sellers input is not signed so we attach empty script bytes
            for (RawTransactionInput rawTransactionInput : takerRawTransactionInputs)
                preparedDepositTx.addInput(getTransactionInput(preparedDepositTx, new byte[]{}, rawTransactionInput));
        } else {
            // taker is buyer role

            // Add buyer inputs
            // the sellers input is not signed so we attach empty script bytes
            for (RawTransactionInput rawTransactionInput : takerRawTransactionInputs)
                preparedDepositTx.addInput(getTransactionInput(preparedDepositTx, new byte[]{}, rawTransactionInput));

            // Add seller inputs
            for (TransactionInput input : offererInputs) {
                preparedDepositTx.addInput(input);
                offererRawTransactionInputs.add(getRawInputFromTransactionInput(input));
            }
        }


        // Add MultiSig output
        Script p2SHMultiSigOutputScript = getP2SHMultiSigOutputScript(buyerPubKey, sellerPubKey, arbitratorPubKey);

        // Tx fee for deposit tx will be paid by buyer.
        TransactionOutput p2SHMultiSigOutput = new TransactionOutput(params, preparedDepositTx, msOutputAmount, p2SHMultiSigOutputScript.getProgram());
        preparedDepositTx.addOutput(p2SHMultiSigOutput);

        // We add the hash ot OP_RETURN with a 0 amount output
        TransactionOutput contractHashOutput = new TransactionOutput(params, preparedDepositTx, Coin.ZERO,
                ScriptBuilder.createOpReturnScript(contractHash).getProgram());
        preparedDepositTx.addOutput(contractHashOutput);

        TransactionOutput takerTransactionOutput = null;
        if (takerChangeOutputValue > 0 && takerChangeAddressString != null)
            takerTransactionOutput = new TransactionOutput(params, preparedDepositTx, Coin.valueOf(takerChangeOutputValue),
                    new Address(params, takerChangeAddressString));

        if (offererIsBuyer) {
            // Add optional buyer outputs 
            if (offererOutput != null)
                preparedDepositTx.addOutput(offererOutput);

            // Add optional seller outputs 
            if (takerTransactionOutput != null)
                preparedDepositTx.addOutput(takerTransactionOutput);
        } else {
            // taker is buyer role

            // Add optional seller outputs 
            if (takerTransactionOutput != null)
                preparedDepositTx.addOutput(takerTransactionOutput);

            // Add optional buyer outputs 
            if (offererOutput != null)
                preparedDepositTx.addOutput(offererOutput);
        }

        // Sign inputs 
        int start = offererIsBuyer ? 0 : takerRawTransactionInputs.size();
        int end = offererIsBuyer ? offererInputs.size() : preparedDepositTx.getInputs().size();
        for (int i = start; i < end; i++) {
            TransactionInput input = preparedDepositTx.getInput(i);
            signInput(preparedDepositTx, input, i);
            checkScriptSig(preparedDepositTx, input, i);
        }

        verifyTransaction(preparedDepositTx);

        printTxWithInputs("preparedDepositTx", preparedDepositTx);

        return new PreparedDepositTxAndOffererInputs(offererRawTransactionInputs, preparedDepositTx.bitcoinSerialize());
    }

    /**
     * The taker signs the deposit transaction he received from the offerer and publishes it.
     *
     * @param takerIsSeller               The flag indicating if we are in the taker as seller role or the opposite.
     * @param contractHash                The hash of the contract to be added to the OP_RETURN output.
     * @param offerersDepositTxSerialized The prepared deposit transaction signed by the offerer.
     * @param buyerInputs                 The connected outputs for all inputs of the buyer.
     * @param sellerInputs                The connected outputs for all inputs of the seller.
     * @param buyerPubKey                 The public key of the buyer.
     * @param sellerPubKey                The public key of the seller.
     * @param arbitratorPubKey            The public key of the arbitrator.
     * @param callback                    Callback when transaction is broadcasted.
     * @throws SigningException
     * @throws TransactionVerificationException
     * @throws WalletException
     */
    public void takerSignsAndPublishesDepositTx(boolean takerIsSeller,
                                                byte[] contractHash,
                                                byte[] offerersDepositTxSerialized,
                                                List<RawTransactionInput> buyerInputs,
                                                List<RawTransactionInput> sellerInputs,
                                                byte[] buyerPubKey,
                                                byte[] sellerPubKey,
                                                byte[] arbitratorPubKey,
                                                FutureCallback<Transaction> callback) throws SigningException, TransactionVerificationException,
            WalletException {
        Transaction offerersDepositTx = new Transaction(params, offerersDepositTxSerialized);

        log.trace("signAndPublishDepositTx called");
        log.trace("takerIsSeller " + takerIsSeller);
        log.trace("offerersDepositTx " + offerersDepositTx.toString());
        log.trace("buyerConnectedOutputsForAllInputs " + buyerInputs.toString());
        log.trace("sellerConnectedOutputsForAllInputs " + sellerInputs.toString());
        log.trace("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.trace("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.trace("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        checkArgument(!buyerInputs.isEmpty());
        checkArgument(!sellerInputs.isEmpty());

        // Check if offerers Multisig script is identical to the takers
        Script p2SHMultiSigOutputScript = getP2SHMultiSigOutputScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        if (!offerersDepositTx.getOutput(0).getScriptPubKey().equals(p2SHMultiSigOutputScript))
            throw new TransactionVerificationException("Offerers p2SHMultiSigOutputScript does not match to takers p2SHMultiSigOutputScript");

        // The outpoints are not available from the serialized offerersDepositTx, so we cannot use that tx directly, but we use it to construct a new 
        // depositTx
        Transaction depositTx = new Transaction(params);

        if (takerIsSeller) {
            // Add buyer inputs and apply signature
            // We grab the signature from the offerersDepositTx and apply it to the new tx input
            for (int i = 0; i < buyerInputs.size(); i++)
                depositTx.addInput(getTransactionInput(depositTx, getScriptProgram(offerersDepositTx, i), buyerInputs.get(i)));

            // Add seller inputs 
            for (RawTransactionInput rawTransactionInput : sellerInputs)
                depositTx.addInput(getTransactionInput(depositTx, new byte[]{}, rawTransactionInput));
        } else {
            // taker is buyer
            // Add buyer inputs and apply signature
            for (RawTransactionInput rawTransactionInput : buyerInputs)
                depositTx.addInput(getTransactionInput(depositTx, new byte[]{}, rawTransactionInput));

            // Add seller inputs 
            // We grab the signature from the offerersDepositTx and apply it to the new tx input
            for (int i = buyerInputs.size(), k = 0; i < offerersDepositTx.getInputs().size(); i++, k++)
                depositTx.addInput(getTransactionInput(depositTx, getScriptProgram(offerersDepositTx, i), sellerInputs.get(k)));
        }

        // Check if OP_RETURN output with contract hash matches the one from the offerer
        TransactionOutput contractHashOutput = new TransactionOutput(params, offerersDepositTx, Coin.ZERO,
                ScriptBuilder.createOpReturnScript(contractHash).getProgram());
        log.debug("contractHashOutput " + contractHashOutput);
        TransactionOutput offerersContractHashOutput = offerersDepositTx.getOutputs().get(1);
        log.debug("offerersContractHashOutput " + offerersContractHashOutput);
        if (!offerersContractHashOutput.getScriptPubKey().equals(contractHashOutput.getScriptPubKey()))
            throw new TransactionVerificationException("Offerers transaction output for the contract hash is not matching takers version.");

        // Add all outputs from offerersDepositTx to depositTx
        offerersDepositTx.getOutputs().forEach(depositTx::addOutput);
        printTxWithInputs("offerersDepositTx", offerersDepositTx);

        // Sign inputs 
        int start = takerIsSeller ? buyerInputs.size() : 0;
        int end = takerIsSeller ? depositTx.getInputs().size() : buyerInputs.size();
        for (int i = start; i < end; i++) {
            TransactionInput input = depositTx.getInput(i);
            signInput(depositTx, input, i);
            checkScriptSig(depositTx, input, i);
        }

        verifyTransaction(depositTx);
        checkWalletConsistency();

        printTxWithInputs("depositTx", depositTx);

        // Broadcast depositTx
        checkNotNull(walletAppKit);
        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(depositTx).future();
        Futures.addCallback(broadcastComplete, callback);
    }


    /**
     * Seller signs payout transaction, buyer has not signed yet.
     *
     * @param depositTx          Deposit transaction
     * @param buyerPayoutAmount  Payout amount for buyer
     * @param sellerPayoutAmount Payout amount for seller
     * @param buyerPayoutAddressString Address for buyer
     * @param sellerPayoutAddressEntry AddressEntry for seller
     * @param lockTime           Lock time
     * @param buyerPubKey        The public key of the buyer.
     * @param sellerPubKey       The public key of the seller.
     * @param arbitratorPubKey   The public key of the arbitrator.
     * @return DER encoded canonical signature
     * @throws AddressFormatException
     * @throws TransactionVerificationException
     */
    public byte[] sellerSignsPayoutTx(Transaction depositTx,
                                      Coin buyerPayoutAmount,
                                      Coin sellerPayoutAmount,
                                      String buyerPayoutAddressString,
                                      AddressEntry sellerPayoutAddressEntry,
                                      AddressEntry multiSigAddressEntry,
                                      long lockTime,
                                      byte[] buyerPubKey,
                                      byte[] sellerPubKey,
                                      byte[] arbitratorPubKey)
            throws AddressFormatException, TransactionVerificationException {
        log.trace("sellerSignsPayoutTx called");
        log.trace("depositTx " + depositTx.toString());
        log.trace("buyerPayoutAmount " + buyerPayoutAmount.toFriendlyString());
        log.trace("sellerPayoutAmount " + sellerPayoutAmount.toFriendlyString());
        log.trace("buyerPayoutAddressString " + buyerPayoutAddressString);
        log.trace("sellerPayoutAddressEntry " + sellerPayoutAddressEntry.toString());
        log.trace("multiSigAddressEntry " + multiSigAddressEntry.toString());
        log.trace("lockTime " + lockTime);
        log.trace("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.trace("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.trace("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        Transaction preparedPayoutTx = createPayoutTx(depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressEntry.getAddressString(),
                lockTime
        );
        // MS redeemScript
        Script redeemScript = getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        // MS output from prev. tx is index 0
        Sha256Hash sigHash = preparedPayoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        DeterministicKey keyPair = multiSigAddressEntry.getKeyPair();
        checkNotNull(keyPair);
        ECKey.ECDSASignature sellerSignature = keyPair.sign(sigHash, aesKey).toCanonicalised();

        verifyTransaction(preparedPayoutTx);

        printTxWithInputs("preparedPayoutTx", preparedPayoutTx);

        return sellerSignature.encodeToDER();
    }

    /**
     * Buyer creates and signs payout transaction and adds signature of seller to complete the transaction
     *
     * @param depositTx           Deposit transaction
     * @param sellerSignature     DER encoded canonical signature of seller
     * @param buyerPayoutAmount   Payout amount for buyer
     * @param sellerPayoutAmount  Payout amount for seller
     * @param buyerPayoutAddressEntry   AddressEntry for buyer
     * @param sellerAddressString Address for seller
     * @param lockTime            Lock time
     * @param buyerPubKey         The public key of the buyer.
     * @param sellerPubKey        The public key of the seller.
     * @param arbitratorPubKey    The public key of the arbitrator.
     * @return The payout transaction
     * @throws AddressFormatException
     * @throws TransactionVerificationException
     * @throws WalletException
     */
    public Transaction buyerSignsAndFinalizesPayoutTx(Transaction depositTx,
                                                      byte[] sellerSignature,
                                                      Coin buyerPayoutAmount,
                                                      Coin sellerPayoutAmount,
                                                      AddressEntry buyerPayoutAddressEntry,
                                                      AddressEntry multiSigAddressEntry,
                                                      String sellerAddressString,
                                                      long lockTime,
                                                      byte[] buyerPubKey,
                                                      byte[] sellerPubKey,
                                                      byte[] arbitratorPubKey)
            throws AddressFormatException, TransactionVerificationException, WalletException {
        log.trace("buyerSignsAndFinalizesPayoutTx called");
        log.trace("depositTx " + depositTx.toString());
        log.trace("sellerSignature r " + ECKey.ECDSASignature.decodeFromDER(sellerSignature).r.toString());
        log.trace("sellerSignature s " + ECKey.ECDSASignature.decodeFromDER(sellerSignature).s.toString());
        log.trace("buyerPayoutAmount " + buyerPayoutAmount.toFriendlyString());
        log.trace("sellerPayoutAmount " + sellerPayoutAmount.toFriendlyString());
        log.trace("buyerPayoutAddressEntry " + buyerPayoutAddressEntry);
        log.trace("multiSigAddressEntry " + multiSigAddressEntry);
        log.trace("sellerAddressString " + sellerAddressString);
        log.trace("lockTime " + lockTime);
        log.trace("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.trace("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.trace("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        Transaction payoutTx = createPayoutTx(depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddressEntry.getAddressString(),
                sellerAddressString,
                lockTime);
        // MS redeemScript
        Script redeemScript = getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        // MS output from prev. tx is index 0
        Sha256Hash sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        checkNotNull(multiSigAddressEntry.getKeyPair(), "multiSigAddressEntry.getKeyPair() must not be null");
        ECKey.ECDSASignature buyerSignature = multiSigAddressEntry.getKeyPair().sign(sigHash, aesKey).toCanonicalised();

        TransactionSignature sellerTxSig = new TransactionSignature(ECKey.ECDSASignature.decodeFromDER(sellerSignature), Transaction.SigHash.ALL, false);
        TransactionSignature buyerTxSig = new TransactionSignature(buyerSignature, Transaction.SigHash.ALL, false);
        // Take care of order of signatures. Need to be reversed here. See comment below at getMultiSigRedeemScript (arbitrator, seller, buyer)
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(sellerTxSig, buyerTxSig), redeemScript);

        TransactionInput input = payoutTx.getInput(0);
        input.setScriptSig(inputScript);

        printTxWithInputs("payoutTx", payoutTx);
        
        verifyTransaction(payoutTx);
        checkWalletConsistency();
        checkScriptSig(payoutTx, input, 0);
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        input.verify(input.getConnectedOutput());

        // As we use lockTime the tx will not be relayed as it is not considered standard.
        // We need to broadcast on our own when we reahced the block height. Both peers will do the broadcast.
        return payoutTx;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * That arbitrator signs the payout transaction
     *
     * @param depositTxSerialized    Serialized deposit tx
     * @param buyerPayoutAmount      The payout amount of the buyer.
     * @param sellerPayoutAmount     The payout amount of the seller.
     * @param arbitratorPayoutAmount The payout amount of the arbitrator.
     * @param buyerAddressString     The address of the buyer.
     * @param sellerAddressString    The address of the seller.
     * @param arbitratorAddressEntry The addressEntry of the arbitrator.
     * @param buyerPubKey            The public key of the buyer.
     * @param sellerPubKey           The public key of the seller.
     * @param arbitratorPubKey       The public key of the arbitrator.
     * @return DER encoded canonical signature of arbitrator
     * @throws AddressFormatException
     * @throws TransactionVerificationException
     */
    public byte[] signDisputedPayoutTx(byte[] depositTxSerialized,
                                       Coin buyerPayoutAmount,
                                       Coin sellerPayoutAmount,
                                       Coin arbitratorPayoutAmount,
                                       String buyerAddressString,
                                       String sellerAddressString,
                                       AddressEntry arbitratorAddressEntry,
                                       byte[] buyerPubKey,
                                       byte[] sellerPubKey,
                                       byte[] arbitratorPubKey)
            throws AddressFormatException, TransactionVerificationException {
        Transaction depositTx = new Transaction(params, depositTxSerialized);
        log.trace("signDisputedPayoutTx called");
        log.trace("depositTx " + depositTx.toString());
        log.trace("buyerPayoutAmount " + buyerPayoutAmount.toFriendlyString());
        log.trace("sellerPayoutAmount " + sellerPayoutAmount.toFriendlyString());
        log.trace("arbitratorPayoutAmount " + arbitratorPayoutAmount.toFriendlyString());
        log.trace("buyerAddressString " + buyerAddressString);
        log.trace("sellerAddressString " + sellerAddressString);
        log.trace("arbitratorAddressEntry " + arbitratorAddressEntry.toString());
        log.trace("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.trace("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.trace("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        // Our MS is index 0
        TransactionOutput p2SHMultiSigOutput = depositTx.getOutput(0);
        Transaction preparedPayoutTx = new Transaction(params);
        preparedPayoutTx.addInput(p2SHMultiSigOutput);
        if (buyerPayoutAmount.isGreaterThan(Coin.ZERO))
            preparedPayoutTx.addOutput(buyerPayoutAmount, new Address(params, buyerAddressString));
        if (sellerPayoutAmount.isGreaterThan(Coin.ZERO))
            preparedPayoutTx.addOutput(sellerPayoutAmount, new Address(params, sellerAddressString));
        if (arbitratorPayoutAmount.isGreaterThan(Coin.ZERO) && arbitratorAddressEntry.getAddressString() != null)
            preparedPayoutTx.addOutput(arbitratorPayoutAmount, new Address(params, arbitratorAddressEntry.getAddressString()));

        // take care of sorting!
        Script redeemScript = getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        Sha256Hash sigHash = preparedPayoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        if (arbitratorAddressEntry.getKeyPair() == null)
            throw new RuntimeException("Unexpected null value: arbitratorAddressEntry.getKeyPair() must not be null");

        ECKey.ECDSASignature arbitratorSignature = arbitratorAddressEntry.getKeyPair().sign(sigHash, aesKey).toCanonicalised();

        verifyTransaction(preparedPayoutTx);

        printTxWithInputs("preparedPayoutTx", preparedPayoutTx);

        return arbitratorSignature.encodeToDER();
    }

    /**
     * A trader who got the signed tx from the arbitrator finalize the payout tx
     *
     * @param depositTxSerialized     Serialized deposit tx
     * @param arbitratorSignature     DER encoded canonical signature of arbitrator
     * @param buyerPayoutAmount       Payout amount of the buyer
     * @param sellerPayoutAmount      Payout amount of the seller
     * @param arbitratorPayoutAmount  Payout amount for arbitrator
     * @param buyerAddressString      The address of the buyer.
     * @param sellerAddressString     The address of the seller.
     * @param arbitratorAddressString The address of the arbitrator.
     * @param tradersMultiSigAddressEntry     The addressEntry of the trader who calls that method
     * @param buyerPubKey             The public key of the buyer.
     * @param sellerPubKey            The public key of the seller.
     * @param arbitratorPubKey        The public key of the arbitrator.
     * @return The completed payout tx
     * @throws AddressFormatException
     * @throws TransactionVerificationException
     * @throws WalletException
     */
    public Transaction traderSignAndFinalizeDisputedPayoutTx(byte[] depositTxSerialized,
                                                             byte[] arbitratorSignature,
                                                             Coin buyerPayoutAmount,
                                                             Coin sellerPayoutAmount,
                                                             Coin arbitratorPayoutAmount,
                                                             String buyerAddressString,
                                                             String sellerAddressString,
                                                             String arbitratorAddressString,
                                                             AddressEntry tradersMultiSigAddressEntry,
                                                             byte[] buyerPubKey,
                                                             byte[] sellerPubKey,
                                                             byte[] arbitratorPubKey)
            throws AddressFormatException, TransactionVerificationException, WalletException {
        Transaction depositTx = new Transaction(params, depositTxSerialized);

        log.trace("signAndFinalizeDisputedPayoutTx called");
        log.trace("depositTx " + depositTx);
        log.trace("arbitratorSignature r " + ECKey.ECDSASignature.decodeFromDER(arbitratorSignature).r.toString());
        log.trace("arbitratorSignature s " + ECKey.ECDSASignature.decodeFromDER(arbitratorSignature).s.toString());
        log.trace("tradersOwnPayoutAmount " + buyerPayoutAmount.toFriendlyString());
        log.trace("sellerPayoutAmount " + sellerPayoutAmount.toFriendlyString());
        log.trace("arbitratorPayoutAmount " + arbitratorPayoutAmount.toFriendlyString());
        log.trace("buyerAddressString " + buyerAddressString);
        log.trace("sellerAddressString " + sellerAddressString);
        log.trace("arbitratorAddressString " + arbitratorAddressString);
        log.trace("tradersMultiSigAddressEntry " + tradersMultiSigAddressEntry);
        log.trace("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.trace("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.trace("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());


        TransactionOutput p2SHMultiSigOutput = depositTx.getOutput(0);
        Transaction payoutTx = new Transaction(params);
        payoutTx.addInput(p2SHMultiSigOutput);
        if (buyerPayoutAmount.isGreaterThan(Coin.ZERO))
            payoutTx.addOutput(buyerPayoutAmount, new Address(params, buyerAddressString));
        if (sellerPayoutAmount.isGreaterThan(Coin.ZERO))
            payoutTx.addOutput(sellerPayoutAmount, new Address(params, sellerAddressString));
        if (arbitratorPayoutAmount.isGreaterThan(Coin.ZERO))
            payoutTx.addOutput(arbitratorPayoutAmount, new Address(params, arbitratorAddressString));

        // take care of sorting!
        Script redeemScript = getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        Sha256Hash sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        DeterministicKey keyPair = tradersMultiSigAddressEntry.getKeyPair();
        checkNotNull(keyPair);
        ECKey.ECDSASignature tradersSignature = keyPair.sign(sigHash, aesKey).toCanonicalised();

        TransactionSignature tradersTxSig = new TransactionSignature(tradersSignature, Transaction.SigHash.ALL, false);
        TransactionSignature arbitratorTxSig = new TransactionSignature(ECKey.ECDSASignature.decodeFromDER(arbitratorSignature),
                Transaction.SigHash.ALL, false);
        // Take care of order of signatures. See comment below at getMultiSigRedeemScript (sort order needed here: arbitrator, seller, buyer)
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(arbitratorTxSig, tradersTxSig), redeemScript);
        TransactionInput input = payoutTx.getInput(0);
        input.setScriptSig(inputScript);

        verifyTransaction(payoutTx);
        checkWalletConsistency();
        checkScriptSig(payoutTx, input, 0);
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        input.verify(input.getConnectedOutput());

        printTxWithInputs("disputed payoutTx", payoutTx);

        // As we use lockTime the tx will not be relayed as it is not considered standard.
        // We need to broadcast on our own when we reached the block height. Both peers will do the broadcast.
        return payoutTx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param tx
     * @param callback
     */
    public void broadcastTx(Transaction tx, FutureCallback<Transaction> callback) {
        checkNotNull(walletAppKit);
        ListenableFuture<Transaction> future = walletAppKit.peerGroup().broadcastTransaction(tx).future();
        Futures.addCallback(future, callback);
    }

    /**
     * @param transaction The transaction to be added to the wallet
     * @return The transaction we added to the wallet, which is different as the one we passed as argument!
     * @throws VerificationException
     */
    public Transaction addTransactionToWallet(Transaction transaction) throws VerificationException {
        Log.traceCall("transaction " + transaction.toString());

        // We need to recreate the transaction otherwise we get a null pointer... 
        Transaction result = new Transaction(params, transaction.bitcoinSerialize());
        result.getConfidence(Context.get()).setSource(TransactionConfidence.Source.SELF);

        if (wallet != null)
            wallet.receivePending(result, null, true);
        return result;
    }

    /**
     * @param serializedTransaction The serialized transaction to be added to the wallet
     * @return The transaction we added to the wallet, which is different as the one we passed as argument!
     * @throws VerificationException
     */
    public Transaction addTransactionToWallet(byte[] serializedTransaction) throws VerificationException {
        Log.traceCall();

        // We need to recreate the tx otherwise we get a null pointer... 
        Transaction transaction = new Transaction(params, serializedTransaction);
        transaction.getConfidence(Context.get()).setSource(TransactionConfidence.Source.NETWORK);
        log.trace("transaction from serializedTransaction: " + transaction.toString());

        if (wallet != null)
            wallet.receivePending(transaction, null, true);
        return transaction;
    }

    /**
     * @param txId The transaction ID of the transaction we want to lookup
     * @return Returns local existing wallet transaction
     * @throws VerificationException
     */
    public Transaction getWalletTx(Sha256Hash txId) throws VerificationException {
        checkNotNull(wallet);
        return wallet.getTransaction(txId);
    }

    /**
     * Returns the height of the last seen best-chain block. Can be 0 if a wallet is brand new or -1 if the wallet
     * is old and doesn't have that data.
     */
    public int getLastBlockSeenHeight() {
        checkNotNull(wallet);
        return wallet.getLastBlockSeenHeight();
    }

    public ListenableFuture<StoredBlock> getBlockHeightFuture(Transaction transaction) {
        checkNotNull(walletAppKit);
        return walletAppKit.chain().getHeightFuture((int) transaction.getLockTime());
    }

    public int getBestChainHeight() {
        checkNotNull(walletAppKit);
        return walletAppKit.chain().getBestChainHeight();
    }

    public void addBlockChainListener(BlockChainListener blockChainListener) {
        checkNotNull(walletAppKit);
        walletAppKit.chain().addListener(blockChainListener);
    }

    public void removeBlockChainListener(BlockChainListener blockChainListener) {
        checkNotNull(walletAppKit);
        walletAppKit.chain().removeListener(blockChainListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @NotNull
    private RawTransactionInput getRawInputFromTransactionInput(@NotNull TransactionInput input) {
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        checkNotNull(input.getConnectedOutput().getParentTransaction(), "input.getConnectedOutput().getParentTransaction() must not be null");
        checkNotNull(input.getValue(), "input.getValue() must not be null");

        return new RawTransactionInput(input.getOutpoint().getIndex(), input.getConnectedOutput().getParentTransaction().bitcoinSerialize(), input.getValue().value);
    }

    private byte[] getScriptProgram(Transaction offerersDepositTx, int i) throws TransactionVerificationException {
        byte[] scriptProgram = offerersDepositTx.getInputs().get(i).getScriptSig().getProgram();
        if (scriptProgram.length == 0)
            throw new TransactionVerificationException("Inputs from offerer not signed.");

        return scriptProgram;
    }

    @NotNull
    private TransactionInput getTransactionInput(Transaction depositTx, byte[] scriptProgram, RawTransactionInput rawTransactionInput) {
        return new TransactionInput(params,
                depositTx,
                scriptProgram,
                new TransactionOutPoint(params, rawTransactionInput.index, new Transaction(params, rawTransactionInput.parentTransaction)),
                Coin.valueOf(rawTransactionInput.value));
    }


    // Don't use ScriptBuilder.createRedeemScript and ScriptBuilder.createP2SHOutputScript as they use a sorting 
    // (Collections.sort(pubKeys, ECKey.PUBKEY_COMPARATOR);) which can lead to a non-matching list of signatures with pubKeys and the executeMultiSig does 
    // not iterate all possible combinations of sig/pubKeys leading to a verification fault. That nasty bug happens just randomly as the list after sorting 
    // might differ from the provided one or not.
    // Changing the while loop in executeMultiSig to fix that does not help as the reference implementation seems to behave the same (not iterating all 
    // possibilities) .
    // Furthermore the executed list is reversed to the provided.
    // Best practice is to provide the list sorted by the least probable successful candidates first (arbitrator is first -> will be last in execution loop, so
    // avoiding unneeded expensive ECKey.verify calls)
    private Script getMultiSigRedeemScript(byte[] buyerPubKey, byte[] sellerPubKey, byte[] arbitratorPubKey) {
        ECKey buyerKey = ECKey.fromPublicOnly(buyerPubKey);
        ECKey sellerKey = ECKey.fromPublicOnly(sellerPubKey);
        ECKey arbitratorKey = ECKey.fromPublicOnly(arbitratorPubKey);
        // Take care of sorting! Need to reverse to the order we use normally (buyer, seller, arbitrator)
        List<ECKey> keys = ImmutableList.of(arbitratorKey, sellerKey, buyerKey);
        return ScriptBuilder.createMultiSigOutputScript(2, keys);
    }

    private Script getP2SHMultiSigOutputScript(byte[] buyerPubKey, byte[] sellerPubKey, byte[] arbitratorPubKey) {
        return ScriptBuilder.createP2SHOutputScript(getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey));
    }

    private Transaction createPayoutTx(Transaction depositTx,
                                       Coin buyerPayoutAmount,
                                       Coin sellerPayoutAmount,
                                       String buyerAddressString,
                                       String sellerAddressString,
                                       long lockTime) throws AddressFormatException {
        TransactionOutput p2SHMultiSigOutput = depositTx.getOutput(0);
        Transaction transaction = new Transaction(params);
        transaction.addInput(p2SHMultiSigOutput);
        transaction.addOutput(buyerPayoutAmount, new Address(params, buyerAddressString));
        transaction.addOutput(sellerPayoutAmount, new Address(params, sellerAddressString));
        if (lockTime != 0) {
            log.info("We use a lockTime of " + lockTime);
            // When using lockTime we need to set sequenceNumber to 0 
            transaction.getInputs().stream().forEach(i -> i.setSequenceNumber(0));
            transaction.setLockTime(lockTime);
        }
        return transaction;
    }

    private static void printTxWithInputs(String tracePrefix, Transaction tx) {
        log.trace(tracePrefix + ": " + tx.toString());
        for (TransactionInput input : tx.getInputs()) {
            if (input.getConnectedOutput() != null)
                log.trace(tracePrefix + " input value: " + input.getConnectedOutput().getValue().toFriendlyString());
            else
                log.trace(tracePrefix + ": Transaction already has inputs but we don't have the connected outputs, so we don't know the value.");
        }
    }

    private void signInput(Transaction transaction, TransactionInput input, int inputIndex) throws SigningException {
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        Script scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        checkNotNull(wallet);
        ECKey sigKey = input.getOutpoint().getConnectedKey(wallet);
        checkNotNull(sigKey, "signInput: sigKey must not be null. input.getOutpoint()=" + input.getOutpoint().toString());
        Sha256Hash hash = transaction.hashForSignature(inputIndex, scriptPubKey, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature signature = sigKey.sign(hash, aesKey);
        TransactionSignature txSig = new TransactionSignature(signature, Transaction.SigHash.ALL, false);
        if (scriptPubKey.isSentToRawPubKey()) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig));
        } else if (scriptPubKey.isSentToAddress()) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig, sigKey));
        } else {
            throw new SigningException("Don't know how to sign for this kind of scriptPubKey: " + scriptPubKey);
        }
    }

    private void checkWalletConsistency() throws WalletException {
        try {
            log.trace("Check if wallet is consistent before commit.");
            checkNotNull(wallet);
            checkState(wallet.isConsistent());
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new WalletException(t);
        }
    }

    private void verifyTransaction(Transaction transaction) throws TransactionVerificationException {
        try {
            log.trace("Verify transaction " + transaction);
            transaction.verify();
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new TransactionVerificationException(t);
        }
    }

    private void checkScriptSig(Transaction transaction, TransactionInput input, int inputIndex) throws TransactionVerificationException {
        try {
            log.trace("Verifies that this script (interpreted as a scriptSig) correctly spends the given scriptPubKey. Check input at index: " + inputIndex);
            checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
            input.getScriptSig().correctlySpends(transaction, inputIndex, input.getConnectedOutput().getScriptPubKey());
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new TransactionVerificationException(t);
        }
    }

    private void removeSignatures(Transaction transaction) {
        for (TransactionInput input : transaction.getInputs()) {
            input.setScriptSig(new Script(new byte[]{}));
        }
    }

    private void addAvailableInputsAndChangeOutputs(Transaction transaction, AddressEntry addressEntry, Address changeAddress) throws WalletException {
        try {
            // Lets let the framework do the work to find the right inputs
            Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(transaction);
            sendRequest.shuffleOutputs = false;
            sendRequest.aesKey = aesKey;
            // We use a fixed fee
            sendRequest.feePerKb = Coin.ZERO;
            sendRequest.fee = FeePolicy.getFixedTxFeeForTrades();
            // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to wait for 1 confirmation)
            sendRequest.coinSelector = new TradeWalletCoinSelector(params, addressEntry.getAddress());
            // We use always the same address in a trade for all transactions
            sendRequest.changeAddress = changeAddress;
            // With the usage of completeTx() we get all the work done with fee calculation, validation and coin selection.
            // We don't commit that tx to the wallet as it will be changed later and it's not signed yet.
            // So it will not change the wallet balance.
            checkNotNull(wallet, "wallet must not be null");
            wallet.completeTx(sendRequest);
        } catch (Throwable t) {
            throw new WalletException(t);
        }
    }

    public void setAddressEntryList(AddressEntryList addressEntryList) {
        this.addressEntryList = addressEntryList;
    }

    public List<AddressEntry> getAddressEntryListAsImmutableList() {
        return ImmutableList.copyOf(addressEntryList);
    }

}
