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

package io.bisq.core.btc.wallet;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.bisq.common.app.Log;
import io.bisq.common.locale.Res;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.data.InputsAndChangeOutput;
import io.bisq.core.btc.data.PreparedDepositTxAndMakerInputs;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.btc.exceptions.SigningException;
import io.bisq.core.btc.exceptions.TransactionVerificationException;
import io.bisq.core.btc.exceptions.WalletException;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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

    private final WalletsSetup walletsSetup;
    private final NetworkParameters params;

    @Nullable
    private Wallet wallet;
    @Nullable
    private WalletConfig walletConfig;
    @Nullable
    private KeyParameter aesKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeWalletService(WalletsSetup walletsSetup) {
        this.walletsSetup = walletsSetup;
        this.params = BisqEnvironment.getParameters();
        walletsSetup.addSetupCompletedHandler(() -> {
            walletConfig = walletsSetup.getWalletConfig();
            wallet = walletsSetup.getBtcWallet();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AesKey
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setAesKey(@Nullable KeyParameter newAesKey) {
        this.aesKey = newAesKey;
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
    public Transaction createBtcTradingFeeTx(Address fundingAddress,
                                             Address reservedForTradeAddress,
                                             Address changeAddress,
                                             Coin reservedFundsForOffer,
                                             boolean useSavingsWallet,
                                             Coin tradingFee,
                                             Coin txFee,
                                             String feeReceiverAddresses,
                                             FutureCallback<Transaction> callback)
            throws InsufficientMoneyException, AddressFormatException {
        log.debug("fundingAddress " + fundingAddress.toString());
        log.debug("reservedForTradeAddress " + reservedForTradeAddress.toString());
        log.debug("changeAddress " + changeAddress.toString());
        log.debug("reservedFundsForOffer " + reservedFundsForOffer.toPlainString());
        log.debug("useSavingsWallet " + useSavingsWallet);
        log.debug("tradingFee " + tradingFee.toPlainString());
        log.debug("txFee " + txFee.toPlainString());
        log.debug("feeReceiverAddresses " + feeReceiverAddresses);

        Transaction tradingFeeTx = new Transaction(params);
        tradingFeeTx.addOutput(tradingFee, Address.fromBase58(params, feeReceiverAddresses));
        // the reserved amount we need for the trade we send to our trade reservedForTradeAddress
        tradingFeeTx.addOutput(reservedFundsForOffer, reservedForTradeAddress);

        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        // In case of double spend we will detect later in the trade process and use a ban score to penalize bad behaviour (not impl. yet)
        SendRequest sendRequest = SendRequest.forTx(tradingFeeTx);
        sendRequest.shuffleOutputs = false;
        sendRequest.aesKey = aesKey;
        if (useSavingsWallet)
            sendRequest.coinSelector = new BtcCoinSelector(walletsSetup.getAddressesByContext(AddressEntry.Context.AVAILABLE));
        else
            sendRequest.coinSelector = new BtcCoinSelector(fundingAddress);
        // We use a fixed fee

        sendRequest.fee = txFee;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.ensureMinRequiredFee = false;

        // Change is optional in case of overpay or use of funds from savings wallet
        sendRequest.changeAddress = changeAddress;

        checkNotNull(wallet, "Wallet must not be null");
        wallet.completeTx(sendRequest);
        WalletService.printTx("tradingFeeTx", tradingFeeTx);

        checkNotNull(walletConfig, "walletConfig must not be null");
        ListenableFuture<Transaction> broadcastComplete = walletConfig.peerGroup().broadcastTransaction(tradingFeeTx).future();
        Futures.addCallback(broadcastComplete, callback);

        return tradingFeeTx;
    }

    public Transaction estimateBtcTradingFeeTxSize(Address fundingAddress,
                                                   Address reservedForTradeAddress,
                                                   Address changeAddress,
                                                   Coin reservedFundsForOffer,
                                                   boolean useSavingsWallet,
                                                   Coin tradingFee,
                                                   Coin txFee,
                                                   String feeReceiverAddresses)
            throws InsufficientMoneyException, AddressFormatException {
        Transaction tradingFeeTx = new Transaction(params);
        tradingFeeTx.addOutput(tradingFee, Address.fromBase58(params, feeReceiverAddresses));
        tradingFeeTx.addOutput(reservedFundsForOffer, reservedForTradeAddress);

        SendRequest sendRequest = SendRequest.forTx(tradingFeeTx);
        sendRequest.shuffleOutputs = false;
        sendRequest.aesKey = aesKey;
        if (useSavingsWallet)
            sendRequest.coinSelector = new BtcCoinSelector(walletsSetup.getAddressesByContext(AddressEntry.Context.AVAILABLE));
        else
            sendRequest.coinSelector = new BtcCoinSelector(fundingAddress);

        sendRequest.fee = txFee;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.ensureMinRequiredFee = false;
        sendRequest.changeAddress = changeAddress;
        checkNotNull(wallet, "Wallet must not be null");
        wallet.completeTx(sendRequest);
        return tradingFeeTx;
    }

    public Transaction completeBsqTradingFeeTx(Transaction preparedBsqTx,
                                               Address fundingAddress,
                                               Address reservedForTradeAddress,
                                               Address changeAddress,
                                               Coin reservedFundsForOffer,
                                               boolean useSavingsWallet,
                                               Coin txFee) throws
            TransactionVerificationException, WalletException,
            InsufficientMoneyException, AddressFormatException {

        log.debug("preparedBsqTx " + preparedBsqTx.toString());
        log.debug("fundingAddress " + fundingAddress.toString());
        log.debug("changeAddress " + changeAddress.toString());
        log.debug("reservedFundsForOffer " + reservedFundsForOffer.toPlainString());
        log.debug("useSavingsWallet " + useSavingsWallet);
        log.debug("txFee " + txFee.toPlainString());

        // preparedBsqTx has following structure:
        // inputs [1-n] BSQ inputs
        // outputs [0-1] BSQ change output
        // mining fee: burned BSQ fee

        // We add BTC mining fee. Result tx looks like:
        // inputs [1-n] BSQ inputs
        // inputs [1-n] BTC inputs
        // outputs [0-1] BSQ change output
        // outputs [1] BTC reservedForTrade output
        // outputs [0-1] BTC change output
        // mining fee: BTC mining fee + burned BSQ fee

        // In case of txs for burned BSQ fees we have no receiver output and it might be that there is no change outputs
        // We need to guarantee that min. 1 valid output is added (OP_RETURN does not count). So we use a higher input
        // for BTC to force an additional change output.

        final int preparedBsqTxInputsSize = preparedBsqTx.getInputs().size();


        // the reserved amount we need for the trade we send to our trade reservedForTradeAddress
        preparedBsqTx.addOutput(reservedFundsForOffer, reservedForTradeAddress);

        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        // In case of double spend we will detect later in the trade process and use a ban score to penalize bad behaviour (not impl. yet)

        // WalletService.printTx("preparedBsqTx", preparedBsqTx);
        SendRequest sendRequest = SendRequest.forTx(preparedBsqTx);
        sendRequest.shuffleOutputs = false;
        sendRequest.aesKey = aesKey;
        if (useSavingsWallet)
            sendRequest.coinSelector = new BtcCoinSelector(walletsSetup.getAddressesByContext(AddressEntry.Context.AVAILABLE));
        else
            sendRequest.coinSelector = new BtcCoinSelector(fundingAddress);
        // We use a fixed fee
        sendRequest.fee = txFee;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.ensureMinRequiredFee = false;

        sendRequest.signInputs = false;

        // Change is optional in case of overpay or use of funds from savings wallet
        sendRequest.changeAddress = changeAddress;

        checkNotNull(wallet, "Wallet must not be null");
        wallet.completeTx(sendRequest);
        Transaction resultTx = sendRequest.tx;

        // Sign all BTC inputs
        for (int i = preparedBsqTxInputsSize; i < resultTx.getInputs().size(); i++) {
            TransactionInput txIn = resultTx.getInputs().get(i);
            checkArgument(txIn.getConnectedOutput() != null && txIn.getConnectedOutput().isMine(wallet),
                    "txIn.getConnectedOutput() is not in our wallet. That must not happen.");
            WalletService.signTransactionInput(wallet, aesKey, resultTx, txIn, i);
            WalletService.checkScriptSig(resultTx, txIn, i);
        }

        WalletService.checkWalletConsistency(wallet);
        WalletService.verifyTransaction(resultTx);

        WalletService.printTx(Res.getBaseCurrencyCode() + " wallet: Signed tx", resultTx);
        return resultTx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We construct the deposit transaction in the way that the buyer is always the first entry (inputs, outputs, MS keys) and then the seller.
    // In the creation of the deposit tx the taker/maker roles are the determining roles instead of buyer/seller.
    // In the payout tx is is the buyer/seller role. We keep the buyer/seller ordering over all transactions to not get confusion with ordering,
    // which is important to follow correctly specially for the order of the MS keys.


    /**
     * The taker creates a dummy transaction to get the input(s) and optional change output for the amount and the takersAddress for that trade.
     * That will be used to send to the maker for creating the deposit transaction.
     *
     * @param inputAmount   Amount of takers input
     * @param txFee         Mining fee
     * @param takersAddress Address of taker
     * @return A data container holding the inputs, the output value and address
     * @throws TransactionVerificationException
     * @throws WalletException
     */
    public InputsAndChangeOutput takerCreatesDepositsTxInputs(Coin inputAmount, Coin txFee, Address takersAddress, Address takersChangeAddress) throws
            TransactionVerificationException, WalletException {
        log.debug("takerCreatesDepositsTxInputs called");
        log.debug("inputAmount " + inputAmount.toFriendlyString());
        log.debug("txFee " + txFee.toFriendlyString());
        log.debug("takersAddress " + takersAddress.toString());

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
        Coin dummyOutputAmount = inputAmount.subtract(txFee);

        Transaction dummyTX = new Transaction(params);
        // The output is just used to get the right inputs and change outputs, so we use an anonymous ECKey, as it will never be used for anything.
        // We don't care about fee calculation differences between the real tx and that dummy tx as we use a static tx fee.
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTX, dummyOutputAmount, new ECKey().toAddress(params));
        dummyTX.addOutput(dummyOutput);

        // Find the needed inputs to pay the output, optionally add 1 change output.
        // Normally only 1 input and no change output is used, but we support multiple inputs and 1 change output.
        // Our spending transaction output is from the create offer fee payment.
        addAvailableInputsAndChangeOutputs(dummyTX, takersAddress, takersChangeAddress, txFee);

        // The completeTx() call signs the input, but we don't want to pass over signed tx inputs so we remove the signature
        WalletService.removeSignatures(dummyTX);

        WalletService.verifyTransaction(dummyTX);

        //WalletService.printTx("dummyTX", dummyTX);

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
     * The maker creates the deposit transaction using the takers input(s) and optional output and signs his input(s).
     *
     * @param makerIsBuyer              The flag indicating if we are in the maker as buyer role or the opposite.
     * @param contractHash              The hash of the contract to be added to the OP_RETURN output.
     * @param makerInputAmount          The input amount of the maker.
     * @param msOutputAmount            The output amount to our MS output.
     * @param takerRawTransactionInputs Raw data for the connected outputs for all inputs of the taker (normally 1 input)
     * @param takerChangeOutputValue    Optional taker change output value
     * @param takerChangeAddressString  Optional taker change address
     * @param makerAddress              The maker's address.
     * @param makerChangeAddress        The maker's change address.
     * @param buyerPubKey               The public key of the buyer.
     * @param sellerPubKey              The public key of the seller.
     * @param arbitratorPubKey          The public key of the arbitrator.
     * @return A data container holding the serialized transaction and the maker raw inputs
     * @throws SigningException
     * @throws TransactionVerificationException
     * @throws WalletException
     */
    public PreparedDepositTxAndMakerInputs makerCreatesAndSignsDepositTx(boolean makerIsBuyer,
                                                                         byte[] contractHash,
                                                                         Coin makerInputAmount,
                                                                         Coin msOutputAmount,
                                                                         List<RawTransactionInput> takerRawTransactionInputs,
                                                                         long takerChangeOutputValue,
                                                                         @Nullable String takerChangeAddressString,
                                                                         Address makerAddress,
                                                                         Address makerChangeAddress,
                                                                         byte[] buyerPubKey,
                                                                         byte[] sellerPubKey,
                                                                         byte[] arbitratorPubKey)
            throws SigningException, TransactionVerificationException, WalletException, AddressFormatException {
        log.debug("makerCreatesAndSignsDepositTx called");
        log.debug("makerIsBuyer " + makerIsBuyer);
        log.debug("makerInputAmount " + makerInputAmount.toFriendlyString());
        log.debug("msOutputAmount " + msOutputAmount.toFriendlyString());
        log.debug("takerRawInputs " + takerRawTransactionInputs.toString());
        log.debug("takerChangeOutputValue " + takerChangeOutputValue);
        log.debug("takerChangeAddressString " + takerChangeAddressString);
        log.debug("makerAddress " + makerAddress);
        log.debug("makerChangeAddress " + makerChangeAddress);
        log.debug("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.debug("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.debug("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        checkArgument(!takerRawTransactionInputs.isEmpty());

        // First we construct a dummy TX to get the inputs and outputs we want to use for the real deposit tx.
        // Similar to the way we did in the createTakerDepositTxInputs method.
        Transaction dummyTx = new Transaction(params);
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTx, makerInputAmount, new ECKey().toAddress(params));
        dummyTx.addOutput(dummyOutput);
        addAvailableInputsAndChangeOutputs(dummyTx, makerAddress, makerChangeAddress, Coin.ZERO);
        // Normally we have only 1 input but we support multiple inputs if the user has paid in with several transactions.
        List<TransactionInput> makerInputs = dummyTx.getInputs();
        TransactionOutput makerOutput = null;

        // We don't support more then 1 optional change output
        checkArgument(dummyTx.getOutputs().size() < 3, "dummyTx.getOutputs().size() >= 3");

        // Only save change outputs, the dummy output is ignored (that's why we start with index 1)
        if (dummyTx.getOutputs().size() > 1)
            makerOutput = dummyTx.getOutput(1);

        // Now we construct the real deposit tx
        Transaction preparedDepositTx = new Transaction(params);

        ArrayList<RawTransactionInput> makerRawTransactionInputs = new ArrayList<>();
        if (makerIsBuyer) {
            // Add buyer inputs
            for (TransactionInput input : makerInputs) {
                preparedDepositTx.addInput(input);
                makerRawTransactionInputs.add(getRawInputFromTransactionInput(input));
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
            for (TransactionInput input : makerInputs) {
                preparedDepositTx.addInput(input);
                makerRawTransactionInputs.add(getRawInputFromTransactionInput(input));
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
                    Address.fromBase58(params, takerChangeAddressString));

        if (makerIsBuyer) {
            // Add optional buyer outputs
            if (makerOutput != null)
                preparedDepositTx.addOutput(makerOutput);

            // Add optional seller outputs
            if (takerTransactionOutput != null)
                preparedDepositTx.addOutput(takerTransactionOutput);
        } else {
            // taker is buyer role

            // Add optional seller outputs
            if (takerTransactionOutput != null)
                preparedDepositTx.addOutput(takerTransactionOutput);

            // Add optional buyer outputs
            if (makerOutput != null)
                preparedDepositTx.addOutput(makerOutput);
        }

        // Sign inputs
        int start = makerIsBuyer ? 0 : takerRawTransactionInputs.size();
        int end = makerIsBuyer ? makerInputs.size() : preparedDepositTx.getInputs().size();
        for (int i = start; i < end; i++) {
            TransactionInput input = preparedDepositTx.getInput(i);
            signInput(preparedDepositTx, input, i);
            WalletService.checkScriptSig(preparedDepositTx, input, i);
        }

        WalletService.printTx("prepared depositTx", preparedDepositTx);

        WalletService.verifyTransaction(preparedDepositTx);

        return new PreparedDepositTxAndMakerInputs(makerRawTransactionInputs, preparedDepositTx.bitcoinSerialize());
    }

    /**
     * The taker signs the deposit transaction he received from the maker and publishes it.
     *
     * @param takerIsSeller             The flag indicating if we are in the taker as seller role or the opposite.
     * @param contractHash              The hash of the contract to be added to the OP_RETURN output.
     * @param makersDepositTxSerialized The prepared deposit transaction signed by the maker.
     * @param buyerInputs               The connected outputs for all inputs of the buyer.
     * @param sellerInputs              The connected outputs for all inputs of the seller.
     * @param buyerPubKey               The public key of the buyer.
     * @param sellerPubKey              The public key of the seller.
     * @param arbitratorPubKey          The public key of the arbitrator.
     * @param callback                  Callback when transaction is broadcasted.
     * @throws SigningException
     * @throws TransactionVerificationException
     * @throws WalletException
     */
    public Transaction takerSignsAndPublishesDepositTx(boolean takerIsSeller,
                                                       byte[] contractHash,
                                                       byte[] makersDepositTxSerialized,
                                                       List<RawTransactionInput> buyerInputs,
                                                       List<RawTransactionInput> sellerInputs,
                                                       byte[] buyerPubKey,
                                                       byte[] sellerPubKey,
                                                       byte[] arbitratorPubKey,
                                                       FutureCallback<Transaction> callback) throws SigningException, TransactionVerificationException,
            WalletException {
        Transaction makersDepositTx = new Transaction(params, makersDepositTxSerialized);

        log.debug("signAndPublishDepositTx called");
        log.debug("takerIsSeller " + takerIsSeller);
        log.debug("makersDepositTx " + makersDepositTx.toString());
        log.debug("buyerConnectedOutputsForAllInputs " + buyerInputs.toString());
        log.debug("sellerConnectedOutputsForAllInputs " + sellerInputs.toString());
        log.debug("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.debug("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.debug("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        checkArgument(!buyerInputs.isEmpty());
        checkArgument(!sellerInputs.isEmpty());

        // Check if maker's Multisig script is identical to the takers
        Script p2SHMultiSigOutputScript = getP2SHMultiSigOutputScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        if (!makersDepositTx.getOutput(0).getScriptPubKey().equals(p2SHMultiSigOutputScript))
            throw new TransactionVerificationException("Maker's p2SHMultiSigOutputScript does not match to takers p2SHMultiSigOutputScript");

        // The outpoints are not available from the serialized makersDepositTx, so we cannot use that tx directly, but we use it to construct a new
        // depositTx
        Transaction depositTx = new Transaction(params);

        if (takerIsSeller) {
            // Add buyer inputs and apply signature
            // We grab the signature from the makersDepositTx and apply it to the new tx input
            for (int i = 0; i < buyerInputs.size(); i++)
                depositTx.addInput(getTransactionInput(depositTx, getScriptProgram(makersDepositTx, i), buyerInputs.get(i)));

            // Add seller inputs
            for (RawTransactionInput rawTransactionInput : sellerInputs)
                depositTx.addInput(getTransactionInput(depositTx, new byte[]{}, rawTransactionInput));
        } else {
            // taker is buyer
            // Add buyer inputs and apply signature
            for (RawTransactionInput rawTransactionInput : buyerInputs)
                depositTx.addInput(getTransactionInput(depositTx, new byte[]{}, rawTransactionInput));

            // Add seller inputs
            // We grab the signature from the makersDepositTx and apply it to the new tx input
            for (int i = buyerInputs.size(), k = 0; i < makersDepositTx.getInputs().size(); i++, k++)
                depositTx.addInput(getTransactionInput(depositTx, getScriptProgram(makersDepositTx, i), sellerInputs.get(k)));
        }

        // Check if OP_RETURN output with contract hash matches the one from the maker
        TransactionOutput contractHashOutput = new TransactionOutput(params, makersDepositTx, Coin.ZERO,
                ScriptBuilder.createOpReturnScript(contractHash).getProgram());
        log.debug("contractHashOutput " + contractHashOutput);
        TransactionOutput makersContractHashOutput = makersDepositTx.getOutputs().get(1);
        log.debug("makersContractHashOutput " + makersContractHashOutput);
        if (!makersContractHashOutput.getScriptPubKey().equals(contractHashOutput.getScriptPubKey()))
            throw new TransactionVerificationException("Maker's transaction output for the contract hash is not matching takers version.");

        // Add all outputs from makersDepositTx to depositTx
        makersDepositTx.getOutputs().forEach(depositTx::addOutput);
        //WalletService.printTx("makersDepositTx", makersDepositTx);

        // Sign inputs
        int start = takerIsSeller ? buyerInputs.size() : 0;
        int end = takerIsSeller ? depositTx.getInputs().size() : buyerInputs.size();
        for (int i = start; i < end; i++) {
            TransactionInput input = depositTx.getInput(i);
            signInput(depositTx, input, i);
            WalletService.checkScriptSig(depositTx, input, i);
        }

        WalletService.printTx("depositTx", depositTx);

        WalletService.verifyTransaction(depositTx);
        WalletService.checkWalletConsistency(wallet);

        // Broadcast depositTx
        checkNotNull(walletConfig);
        ListenableFuture<Transaction> broadcastComplete = walletConfig.peerGroup().broadcastTransaction(depositTx).future();
        Futures.addCallback(broadcastComplete, callback);

        return depositTx;
    }


    /**
     * Seller signs payout transaction, buyer has not signed yet.
     *
     * @param depositTx                 Deposit transaction
     * @param buyerPayoutAmount         Payout amount for buyer
     * @param sellerPayoutAmount        Payout amount for seller
     * @param buyerPayoutAddressString  Address for buyer
     * @param sellerPayoutAddressString Address for seller
     * @param multiSigKeyPair           DeterministicKey for MultiSig from seller
     * @param buyerPubKey               The public key of the buyer.
     * @param sellerPubKey              The public key of the seller.
     * @param arbitratorPubKey          The public key of the arbitrator.
     * @return DER encoded canonical signature
     * @throws AddressFormatException
     * @throws TransactionVerificationException
     */
    public byte[] buyerSignsPayoutTx(Transaction depositTx,
                                     Coin buyerPayoutAmount,
                                     Coin sellerPayoutAmount,
                                     String buyerPayoutAddressString,
                                     String sellerPayoutAddressString,
                                     DeterministicKey multiSigKeyPair,
                                     byte[] buyerPubKey,
                                     byte[] sellerPubKey,
                                     byte[] arbitratorPubKey)
            throws AddressFormatException, TransactionVerificationException {
        log.trace("sellerSignsPayoutTx called");
        log.trace("depositTx " + depositTx.toString());
        log.trace("buyerPayoutAmount " + buyerPayoutAmount.toFriendlyString());
        log.trace("sellerPayoutAmount " + sellerPayoutAmount.toFriendlyString());
        log.trace("buyerPayoutAddressString " + buyerPayoutAddressString);
        log.trace("sellerPayoutAddressString " + sellerPayoutAddressString);
        log.trace("multiSigKeyPair (not displayed for security reasons)");
        log.info("buyerPubKey HEX=" + ECKey.fromPublicOnly(buyerPubKey).getPublicKeyAsHex());
        log.info("sellerPubKey HEX=" + ECKey.fromPublicOnly(sellerPubKey).getPublicKeyAsHex());
        log.info("arbitratorPubKey HEX=" + ECKey.fromPublicOnly(arbitratorPubKey).getPublicKeyAsHex());
        Transaction preparedPayoutTx = createPayoutTx(depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString);
        // MS redeemScript
        Script redeemScript = getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        // MS output from prev. tx is index 0
        Sha256Hash sigHash = preparedPayoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        checkNotNull(multiSigKeyPair, "multiSigKeyPair must not be null");
        if (multiSigKeyPair.isEncrypted())
            checkNotNull(aesKey);

        ECKey.ECDSASignature buyerSignature = multiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();

        WalletService.printTx("prepared payoutTx", preparedPayoutTx);

        WalletService.verifyTransaction(preparedPayoutTx);

        return buyerSignature.encodeToDER();
    }


    /**
     * Buyer creates and signs payout transaction and adds signature of seller to complete the transaction
     *
     * @param depositTx                 Deposit transaction
     * @param buyerSignature            DER encoded canonical signature of seller
     * @param buyerPayoutAmount         Payout amount for buyer
     * @param sellerPayoutAmount        Payout amount for seller
     * @param buyerPayoutAddressString  Address for buyer
     * @param sellerPayoutAddressString Address for seller
     * @param multiSigKeyPair           Buyer's keypair for MultiSig
     * @param buyerPubKey               The public key of the buyer.
     * @param sellerPubKey              The public key of the seller.
     * @param arbitratorPubKey          The public key of the arbitrator.
     * @return The payout transaction
     * @throws AddressFormatException
     * @throws TransactionVerificationException
     * @throws WalletException
     */
    public Transaction sellerSignsAndFinalizesPayoutTx(Transaction depositTx,
                                                       byte[] buyerSignature,
                                                       Coin buyerPayoutAmount,
                                                       Coin sellerPayoutAmount,
                                                       String buyerPayoutAddressString,
                                                       String sellerPayoutAddressString,
                                                       DeterministicKey multiSigKeyPair,
                                                       byte[] buyerPubKey,
                                                       byte[] sellerPubKey,
                                                       byte[] arbitratorPubKey)
            throws AddressFormatException, TransactionVerificationException, WalletException {
        log.trace("buyerSignsAndFinalizesPayoutTx called");
        log.trace("depositTx " + depositTx.toString());
        log.trace("buyerSignature r " + ECKey.ECDSASignature.decodeFromDER(buyerSignature).r.toString());
        log.trace("buyerSignature s " + ECKey.ECDSASignature.decodeFromDER(buyerSignature).s.toString());
        log.trace("buyerPayoutAmount " + buyerPayoutAmount.toFriendlyString());
        log.trace("sellerPayoutAmount " + sellerPayoutAmount.toFriendlyString());
        log.trace("buyerPayoutAddressString " + buyerPayoutAddressString);
        log.trace("sellerPayoutAddressString " + sellerPayoutAddressString);
        log.trace("multiSigKeyPair (not displayed for security reasons)");
        log.info("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.info("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.info("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        Transaction payoutTx = createPayoutTx(depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString);
        // MS redeemScript
        Script redeemScript = getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        // MS output from prev. tx is index 0
        Sha256Hash sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        checkNotNull(multiSigKeyPair, "multiSigKeyPair must not be null");
        if (multiSigKeyPair.isEncrypted())
            checkNotNull(aesKey);


        ECKey.ECDSASignature sellerSignature = multiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();

        TransactionSignature buyerTxSig = new TransactionSignature(ECKey.ECDSASignature.decodeFromDER(buyerSignature),
                Transaction.SigHash.ALL, false);
        TransactionSignature sellerTxSig = new TransactionSignature(sellerSignature, Transaction.SigHash.ALL, false);
        // Take care of order of signatures. Need to be reversed here. See comment below at getMultiSigRedeemScript (arbitrator, seller, buyer)
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(sellerTxSig, buyerTxSig), redeemScript);

        TransactionInput input = payoutTx.getInput(0);
        input.setScriptSig(inputScript);

        WalletService.printTx("payoutTx", payoutTx);

        WalletService.verifyTransaction(payoutTx);
        WalletService.checkWalletConsistency(wallet);
        WalletService.checkScriptSig(payoutTx, input, 0);
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        input.verify(input.getConnectedOutput());
        return payoutTx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * That arbitrator signs the payout transaction
     *
     * @param depositTxSerialized Serialized deposit tx
     * @param buyerPayoutAmount   The payout amount of the buyer.
     * @param sellerPayoutAmount  The payout amount of the seller.
     * @param buyerAddressString  The address of the buyer.
     * @param sellerAddressString The address of the seller.
     * @param arbitratorKeyPair   The keypair of the arbitrator.
     * @param buyerPubKey         The public key of the buyer.
     * @param sellerPubKey        The public key of the seller.
     * @param arbitratorPubKey    The public key of the arbitrator.
     * @return DER encoded canonical signature of arbitrator
     * @throws AddressFormatException
     * @throws TransactionVerificationException
     */
    public byte[] arbitratorSignsDisputedPayoutTx(byte[] depositTxSerialized,
                                                  Coin buyerPayoutAmount,
                                                  Coin sellerPayoutAmount,
                                                  String buyerAddressString,
                                                  String sellerAddressString,
                                                  DeterministicKey arbitratorKeyPair,
                                                  byte[] buyerPubKey,
                                                  byte[] sellerPubKey,
                                                  byte[] arbitratorPubKey)
            throws AddressFormatException, TransactionVerificationException {
        Transaction depositTx = new Transaction(params, depositTxSerialized);
        log.trace("signDisputedPayoutTx called");
        log.trace("depositTx " + depositTx.toString());
        log.trace("buyerPayoutAmount " + buyerPayoutAmount.toFriendlyString());
        log.trace("sellerPayoutAmount " + sellerPayoutAmount.toFriendlyString());
        log.trace("buyerAddressString " + buyerAddressString);
        log.trace("sellerAddressString " + sellerAddressString);
        log.trace("arbitratorKeyPair (not displayed for security reasons)");
        log.info("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.info("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.info("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        // Our MS is index 0
        TransactionOutput p2SHMultiSigOutput = depositTx.getOutput(0);
        Transaction preparedPayoutTx = new Transaction(params);
        preparedPayoutTx.addInput(p2SHMultiSigOutput);
        if (buyerPayoutAmount.isGreaterThan(Coin.ZERO))
            preparedPayoutTx.addOutput(buyerPayoutAmount, Address.fromBase58(params, buyerAddressString));
        if (sellerPayoutAmount.isGreaterThan(Coin.ZERO))
            preparedPayoutTx.addOutput(sellerPayoutAmount, Address.fromBase58(params, sellerAddressString));

        // take care of sorting!
        Script redeemScript = getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        Sha256Hash sigHash = preparedPayoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        checkNotNull(arbitratorKeyPair, "arbitratorKeyPair must not be null");
        if (arbitratorKeyPair.isEncrypted())
            checkNotNull(aesKey);

        ECKey.ECDSASignature arbitratorSignature = arbitratorKeyPair.sign(sigHash, aesKey).toCanonicalised();

        WalletService.verifyTransaction(preparedPayoutTx);

        //WalletService.printTx("preparedPayoutTx", preparedPayoutTx);

        return arbitratorSignature.encodeToDER();
    }

    /**
     * A trader who got the signed tx from the arbitrator finalizes the payout tx
     *
     * @param depositTxSerialized    Serialized deposit tx
     * @param arbitratorSignature    DER encoded canonical signature of arbitrator
     * @param buyerPayoutAmount      Payout amount of the buyer
     * @param sellerPayoutAmount     Payout amount of the seller
     * @param buyerAddressString     The address of the buyer.
     * @param sellerAddressString    The address of the seller.
     * @param tradersMultiSigKeyPair The keypair for the MultiSig of the trader who calls that method
     * @param buyerPubKey            The public key of the buyer.
     * @param sellerPubKey           The public key of the seller.
     * @param arbitratorPubKey       The public key of the arbitrator.
     * @return The completed payout tx
     * @throws AddressFormatException
     * @throws TransactionVerificationException
     * @throws WalletException
     */
    public Transaction traderSignAndFinalizeDisputedPayoutTx(byte[] depositTxSerialized,
                                                             byte[] arbitratorSignature,
                                                             Coin buyerPayoutAmount,
                                                             Coin sellerPayoutAmount,
                                                             String buyerAddressString,
                                                             String sellerAddressString,
                                                             DeterministicKey tradersMultiSigKeyPair,
                                                             byte[] buyerPubKey,
                                                             byte[] sellerPubKey,
                                                             byte[] arbitratorPubKey)
            throws AddressFormatException, TransactionVerificationException, WalletException {
        Transaction depositTx = new Transaction(params, depositTxSerialized);

        log.trace("signAndFinalizeDisputedPayoutTx called");
        log.trace("depositTx " + depositTx);
        log.trace("arbitratorSignature r " + ECKey.ECDSASignature.decodeFromDER(arbitratorSignature).r.toString());
        log.trace("arbitratorSignature s " + ECKey.ECDSASignature.decodeFromDER(arbitratorSignature).s.toString());
        log.trace("buyerPayoutAmount " + buyerPayoutAmount.toFriendlyString());
        log.trace("sellerPayoutAmount " + sellerPayoutAmount.toFriendlyString());
        log.trace("buyerAddressString " + buyerAddressString);
        log.trace("sellerAddressString " + sellerAddressString);
        log.trace("tradersMultiSigKeyPair (not displayed for security reasons)");
        log.info("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.info("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.info("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());


        TransactionOutput p2SHMultiSigOutput = depositTx.getOutput(0);
        Transaction payoutTx = new Transaction(params);
        payoutTx.addInput(p2SHMultiSigOutput);
        if (buyerPayoutAmount.isGreaterThan(Coin.ZERO))
            payoutTx.addOutput(buyerPayoutAmount, Address.fromBase58(params, buyerAddressString));
        if (sellerPayoutAmount.isGreaterThan(Coin.ZERO))
            payoutTx.addOutput(sellerPayoutAmount, Address.fromBase58(params, sellerAddressString));

        // take care of sorting!
        Script redeemScript = getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        Sha256Hash sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        checkNotNull(tradersMultiSigKeyPair, "tradersMultiSigKeyPair must not be null");
        if (tradersMultiSigKeyPair.isEncrypted())
            checkNotNull(aesKey);
        ECKey.ECDSASignature tradersSignature = tradersMultiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();

        TransactionSignature tradersTxSig = new TransactionSignature(tradersSignature, Transaction.SigHash.ALL, false);
        TransactionSignature arbitratorTxSig = new TransactionSignature(ECKey.ECDSASignature.decodeFromDER(arbitratorSignature),
                Transaction.SigHash.ALL, false);
        // Take care of order of signatures. See comment below at getMultiSigRedeemScript (sort order needed here: arbitrator, seller, buyer)
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(arbitratorTxSig, tradersTxSig), redeemScript);
        TransactionInput input = payoutTx.getInput(0);
        input.setScriptSig(inputScript);

        WalletService.printTx("disputed payoutTx", payoutTx);

        WalletService.verifyTransaction(payoutTx);
        WalletService.checkWalletConsistency(wallet);
        WalletService.checkScriptSig(payoutTx, input, 0);
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        input.verify(input.getConnectedOutput());
        return payoutTx;
    }


    // Emergency payout tool. Used only in cased when the payput from the arbitrator does not work because some data
    // in the trade/dispute are messed up.
    // We keep here arbitratorPayoutAmount just in case (requires cooperation from peer anyway)
    public Transaction emergencySignAndPublishPayoutTx(String depositTxHex,
                                                       Coin buyerPayoutAmount,
                                                       Coin sellerPayoutAmount,
                                                       Coin arbitratorPayoutAmount,
                                                       Coin txFee,
                                                       String buyerAddressString,
                                                       String sellerAddressString,
                                                       String arbitratorAddressString,
                                                       @Nullable String buyerPrivateKeyAsHex,
                                                       @Nullable String sellerPrivateKeyAsHex,
                                                       String arbitratorPrivateKeyAsHex,
                                                       String buyerPubKeyAsHex,
                                                       String sellerPubKeyAsHex,
                                                       String arbitratorPubKeyAsHex,
                                                       String P2SHMultiSigOutputScript,
                                                       FutureCallback<Transaction> callback)
            throws AddressFormatException, TransactionVerificationException, WalletException {
        log.info("signAndPublishPayoutTx called");
        log.info("depositTxHex " + depositTxHex);
        log.info("buyerPayoutAmount " + buyerPayoutAmount.toFriendlyString());
        log.info("sellerPayoutAmount " + sellerPayoutAmount.toFriendlyString());
        log.info("arbitratorPayoutAmount " + arbitratorPayoutAmount.toFriendlyString());
        log.info("buyerAddressString " + buyerAddressString);
        log.info("sellerAddressString " + sellerAddressString);
        log.info("arbitratorAddressString " + arbitratorAddressString);
        log.info("buyerPrivateKeyAsHex (not displayed for security reasons)");
        log.info("sellerPrivateKeyAsHex (not displayed for security reasons)");
        log.info("arbitratorPrivateKeyAsHex (not displayed for security reasons)");
        log.info("buyerPubKeyAsHex " + buyerPubKeyAsHex);
        log.info("sellerPubKeyAsHex " + sellerPubKeyAsHex);
        log.info("arbitratorPubKeyAsHex " + arbitratorPubKeyAsHex);
        log.info("P2SHMultiSigOutputScript " + P2SHMultiSigOutputScript);

        checkNotNull((buyerPrivateKeyAsHex != null || sellerPrivateKeyAsHex != null), "either buyerPrivateKeyAsHex or sellerPrivateKeyAsHex must not be null");

        byte[] buyerPubKey = ECKey.fromPublicOnly(Utils.HEX.decode(buyerPubKeyAsHex)).getPubKey();
        byte[] sellerPubKey = ECKey.fromPublicOnly(Utils.HEX.decode(sellerPubKeyAsHex)).getPubKey();
        final byte[] arbitratorPubKey = ECKey.fromPublicOnly(Utils.HEX.decode(arbitratorPubKeyAsHex)).getPubKey();

        Script p2SHMultiSigOutputScript = getP2SHMultiSigOutputScript(buyerPubKey, sellerPubKey, arbitratorPubKey);

        Coin msOutput = buyerPayoutAmount.add(sellerPayoutAmount).add(arbitratorPayoutAmount).add(txFee);
        TransactionOutput p2SHMultiSigOutput = new TransactionOutput(params, null, msOutput, p2SHMultiSigOutputScript.getProgram());
        Transaction depositTx = new Transaction(params);
        depositTx.addOutput(p2SHMultiSigOutput);

        Transaction payoutTx = new Transaction(params);
        Sha256Hash spendTxHash = Sha256Hash.wrap(depositTxHex);
        payoutTx.addInput(new TransactionInput(params, depositTx, p2SHMultiSigOutputScript.getProgram(), new TransactionOutPoint(params, 0, spendTxHash), msOutput));

        if (buyerPayoutAmount.isGreaterThan(Coin.ZERO))
            payoutTx.addOutput(buyerPayoutAmount, Address.fromBase58(params, buyerAddressString));
        if (sellerPayoutAmount.isGreaterThan(Coin.ZERO))
            payoutTx.addOutput(sellerPayoutAmount, Address.fromBase58(params, sellerAddressString));
        if (arbitratorPayoutAmount.isGreaterThan(Coin.ZERO))
            payoutTx.addOutput(arbitratorPayoutAmount, Address.fromBase58(params, arbitratorAddressString));

        // take care of sorting!
        Script redeemScript = getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        Sha256Hash sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);

        ECKey.ECDSASignature tradersSignature;
        if (buyerPrivateKeyAsHex != null && !buyerPrivateKeyAsHex.isEmpty()) {
            final ECKey buyerPrivateKey = ECKey.fromPrivate(Utils.HEX.decode(buyerPrivateKeyAsHex));
            checkNotNull(buyerPrivateKey, "buyerPrivateKey must not be null");
            tradersSignature = buyerPrivateKey.sign(sigHash, aesKey).toCanonicalised();
        } else {
            checkNotNull(sellerPrivateKeyAsHex, "sellerPrivateKeyAsHex must not be null");
            final ECKey sellerPrivateKey = ECKey.fromPrivate(Utils.HEX.decode(sellerPrivateKeyAsHex));
            checkNotNull(sellerPrivateKey, "sellerPrivateKey must not be null");
            tradersSignature = sellerPrivateKey.sign(sigHash, aesKey).toCanonicalised();
        }
        final ECKey key = ECKey.fromPrivate(Utils.HEX.decode(arbitratorPrivateKeyAsHex));
        checkNotNull(key, "key must not be null");
        ECKey.ECDSASignature arbitratorSignature = key.sign(sigHash, aesKey).toCanonicalised();

        TransactionSignature tradersTxSig = new TransactionSignature(tradersSignature, Transaction.SigHash.ALL, false);
        TransactionSignature arbitratorTxSig = new TransactionSignature(arbitratorSignature, Transaction.SigHash.ALL, false);
        // Take care of order of signatures. See comment below at getMultiSigRedeemScript (sort order needed here: arbitrator, seller, buyer)
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(arbitratorTxSig, tradersTxSig), redeemScript);
        TransactionInput input = payoutTx.getInput(0);
        input.setScriptSig(inputScript);

        WalletService.printTx("payoutTx", payoutTx);

        WalletService.verifyTransaction(payoutTx);
        WalletService.checkWalletConsistency(wallet);

        if (walletConfig != null) {
            ListenableFuture<Transaction> future = walletConfig.peerGroup().broadcastTransaction(payoutTx).future();
            Futures.addCallback(future, callback);
        }

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
        checkNotNull(walletConfig);
        ListenableFuture<Transaction> future = walletConfig.peerGroup().broadcastTransaction(tx).future();
        Futures.addCallback(future, callback);
    }

    /**
     * @param transaction The transaction to be added to the wallet
     * @return The transaction we added to the wallet, which is different as the one we passed as argument!
     * @throws VerificationException
     */
    public Transaction addTxToWallet(Transaction transaction) throws VerificationException {
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
    public Transaction addTxToWallet(byte[] serializedTransaction) throws VerificationException {
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

    public void commitTx(Transaction tx) {
        checkNotNull(wallet);
        wallet.commitTx(tx);
    }

    public Transaction getClonedTransaction(Transaction tx) {
        return new Transaction(params, tx.bitcoinSerialize());
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

    private byte[] getScriptProgram(Transaction makersDepositTx, int i) throws TransactionVerificationException {
        byte[] scriptProgram = makersDepositTx.getInputs().get(i).getScriptSig().getProgram();
        if (scriptProgram.length == 0)
            throw new TransactionVerificationException("Inputs from maker not signed.");

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
                                       String sellerAddressString) throws AddressFormatException {
        TransactionOutput p2SHMultiSigOutput = depositTx.getOutput(0);
        Transaction transaction = new Transaction(params);
        transaction.addInput(p2SHMultiSigOutput);
        transaction.addOutput(buyerPayoutAmount, Address.fromBase58(params, buyerAddressString));
        transaction.addOutput(sellerPayoutAmount, Address.fromBase58(params, sellerAddressString));
        return transaction;
    }

    private void signInput(Transaction transaction, TransactionInput input, int inputIndex) throws SigningException {
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        Script scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        checkNotNull(wallet);
        ECKey sigKey = input.getOutpoint().getConnectedKey(wallet);
        checkNotNull(sigKey, "signInput: sigKey must not be null. input.getOutpoint()=" + input.getOutpoint().toString());
        if (sigKey.isEncrypted())
            checkNotNull(aesKey);
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

    private void addAvailableInputsAndChangeOutputs(Transaction transaction, Address address, Address changeAddress, Coin txFee) throws WalletException {
        try {
            // Lets let the framework do the work to find the right inputs
            SendRequest sendRequest = SendRequest.forTx(transaction);
            sendRequest.shuffleOutputs = false;
            sendRequest.aesKey = aesKey;
            // We use a fixed fee
            sendRequest.fee = txFee;
            sendRequest.feePerKb = Coin.ZERO;
            sendRequest.ensureMinRequiredFee = false;
            // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to wait for 1 confirmation)
            sendRequest.coinSelector = new BtcCoinSelector(address);
            // We use always the same address in a trade for all transactions
            sendRequest.changeAddress = changeAddress;
            // With the usage of completeTx() we get all the work done with fee calculation, validation and coin selection.
            // We don't commit that tx to the wallet as it will be changed later and it's not signed yet.
            // So it will not change the wallet balance.
            checkNotNull(wallet, "wallet must not be null");
            // TODO we got here exceptions with missing funds. Not reproducable but leave log for better debugging.
            log.info("print tx before wallet.completeTx: " + sendRequest.tx.toString());
            wallet.completeTx(sendRequest);
        } catch (Throwable t) {
            throw new WalletException(t);
        }
    }
}
