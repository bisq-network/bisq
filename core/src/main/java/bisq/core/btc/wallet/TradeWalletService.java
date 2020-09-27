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

package bisq.core.btc.wallet;

import bisq.core.btc.exceptions.SigningException;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.InputsAndChangeOutput;
import bisq.core.btc.model.PreparedDepositTxAndMakerInputs;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.setup.WalletConfig;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import bisq.common.config.Config;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import javax.inject.Inject;

import com.google.common.collect.ImmutableList;

import org.bouncycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class TradeWalletService {
    private static final Logger log = LoggerFactory.getLogger(TradeWalletService.class);

    private final WalletsSetup walletsSetup;
    private final Preferences preferences;
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
    public TradeWalletService(WalletsSetup walletsSetup, Preferences preferences) {
        this.walletsSetup = walletsSetup;
        this.preferences = preferences;
        this.params = Config.baseCurrencyNetworkParameters();
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
     * Create a BTC trading fee transaction for the maker or taker of an offer. The first output of the tx is for the
     * fee receiver. The second output is the reserve of the trade. There is an optional third output for change.
     *
     * @param fundingAddress          the provided source of funds in case the savings wallet is not used
     * @param reservedForTradeAddress the address of the trade reserve
     * @param changeAddress           the change address to use in case of overpayment or use of the savings wallet
     * @param reservedFundsForOffer   the amount to reserve for the trade
     * @param useSavingsWallet        {@code true} to use the savings wallet, {@code false} to use the funding address
     * @param tradingFee              the amount of the trading fee
     * @param txFee                   the mining fee for this transaction
     * @param feeReceiverAddress      the address of the receiver of the trading fee
     * @param doBroadcast             {@code true} to broadcast the transaction, {@code false} otherwise
     * @param callback                an optional callback to use when broadcasting the transaction
     * @return the optionally broadcast transaction
     * @throws InsufficientMoneyException if the request could not be completed due to not enough balance
     * @throws AddressFormatException if the fee receiver base58 address doesn't parse or its checksum is invalid
     */
    public Transaction createBtcTradingFeeTx(Address fundingAddress,
                                             Address reservedForTradeAddress,
                                             Address changeAddress,
                                             Coin reservedFundsForOffer,
                                             boolean useSavingsWallet,
                                             Coin tradingFee,
                                             Coin txFee,
                                             String feeReceiverAddress,
                                             boolean doBroadcast,
                                             @Nullable TxBroadcaster.Callback callback) throws InsufficientMoneyException, AddressFormatException {
        Transaction tradingFeeTx = new Transaction(params);
        SendRequest sendRequest = null;
        try {
            tradingFeeTx.addOutput(tradingFee, LegacyAddress.fromBase58(params, feeReceiverAddress));
            // the reserved amount we need for the trade we send to our trade reservedForTradeAddress
            tradingFeeTx.addOutput(reservedFundsForOffer, reservedForTradeAddress);

            // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
            // wait for 1 confirmation)
            // In case of double spend we will detect later in the trade process and use a ban score to penalize bad behaviour (not impl. yet)
            sendRequest = SendRequest.forTx(tradingFeeTx);
            sendRequest.shuffleOutputs = false;
            sendRequest.aesKey = aesKey;
            if (useSavingsWallet) {
                sendRequest.coinSelector = new BtcCoinSelector(walletsSetup.getAddressesByContext(AddressEntry.Context.AVAILABLE),
                        preferences.getIgnoreDustThreshold());
            } else {
                sendRequest.coinSelector = new BtcCoinSelector(fundingAddress, preferences.getIgnoreDustThreshold());
            }
            // We use a fixed fee

            sendRequest.fee = txFee;
            sendRequest.feePerKb = Coin.ZERO;
            sendRequest.ensureMinRequiredFee = false;

            // Change is optional in case of overpay or use of funds from savings wallet
            sendRequest.changeAddress = changeAddress;

            checkNotNull(wallet, "Wallet must not be null");
            wallet.completeTx(sendRequest);
            if (removeDust(tradingFeeTx)) {
                wallet.signTransaction(sendRequest);
            }
            WalletService.printTx("tradingFeeTx", tradingFeeTx);

            if (doBroadcast && callback != null) {
                broadcastTx(tradingFeeTx, callback);
            }

            return tradingFeeTx;
        } catch (Throwable t) {
            if (wallet != null && sendRequest != null && sendRequest.coinSelector != null) {
                log.warn("Balance = {}; CoinSelector = {}", wallet.getBalance(sendRequest.coinSelector), sendRequest.coinSelector);
            }

            log.warn("createBtcTradingFeeTx failed: tradingFeeTx={}, txOutputs={}", tradingFeeTx.toString(),
                    tradingFeeTx.getOutputs());
            throw t;
        }
    }

    public Transaction completeBsqTradingFeeTx(Transaction preparedBsqTx,
                                               Address fundingAddress,
                                               Address reservedForTradeAddress,
                                               Address changeAddress,
                                               Coin reservedFundsForOffer,
                                               boolean useSavingsWallet,
                                               Coin txFee)
            throws TransactionVerificationException, WalletException, InsufficientMoneyException, AddressFormatException {
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

        // In case all BSQ were burnt as fees we have no receiver output and it might be that there are no change outputs
        // We need to guarantee that min. 1 valid output is added (OP_RETURN does not count). So we use a higher input
        // for BTC to force an additional change output.

        final int preparedBsqTxInputsSize = preparedBsqTx.getInputs().size();
        final boolean hasBsqOutputs = !preparedBsqTx.getOutputs().isEmpty();

        // If there are no BSQ change outputs an output larger than the burnt BSQ amount has to be added as the first
        // output to make sure the reserved funds are in output 1, deposit tx input creation depends on the reserve
        // being output 1. The amount has to be larger than the BSQ input to make sure the inputs get burnt.
        // The BTC changeAddress is used, so it might get used for both output 0 and output 2.
        if (!hasBsqOutputs) {
            var bsqInputValue = preparedBsqTx.getInputs().stream()
                    .map(TransactionInput::getValue)
                    .reduce(Coin.valueOf(0), Coin::add);

            preparedBsqTx.addOutput(bsqInputValue.add(Coin.valueOf(1)), changeAddress);
        }
        // the reserved amount we need for the trade we send to our trade reservedForTradeAddress
        preparedBsqTx.addOutput(reservedFundsForOffer, reservedForTradeAddress);

        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        // In case of double spend we will detect later in the trade process and use a ban score to penalize bad behaviour (not impl. yet)

        SendRequest sendRequest = SendRequest.forTx(preparedBsqTx);
        sendRequest.shuffleOutputs = false;
        sendRequest.aesKey = aesKey;
        if (useSavingsWallet) {
            sendRequest.coinSelector = new BtcCoinSelector(walletsSetup.getAddressesByContext(AddressEntry.Context.AVAILABLE),
                    preferences.getIgnoreDustThreshold());
        } else {
            sendRequest.coinSelector = new BtcCoinSelector(fundingAddress, preferences.getIgnoreDustThreshold());
        }
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
        removeDust(resultTx);

        // Sign all BTC inputs
        for (int i = preparedBsqTxInputsSize; i < resultTx.getInputs().size(); i++) {
            TransactionInput txIn = resultTx.getInputs().get(i);
            checkArgument(txIn.getConnectedOutput() != null &&
                            txIn.getConnectedOutput().isMine(wallet),
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
    // Deposit tx
    ///////////////////////////////////////////////////////////////////////////////////////////


    // We construct the deposit transaction in the way that the buyer is always the first entry (inputs, outputs, MS keys) and then the seller.
    // In the creation of the deposit tx the taker/maker roles are the determining roles instead of buyer/seller.
    // In the payout tx it is the buyer/seller role. We keep the buyer/seller ordering over all transactions to not get confusion with ordering,
    // which is important to follow correctly specially for the order of the MS keys.


    /**
     * The taker creates a dummy transaction to get the input(s) and optional change output for the amount and the
     * taker's address for that trade. That will be used to send to the maker for creating the deposit transaction.
     *
     * @param takeOfferFeeTx the take offer fee tx
     * @param inputAmount    amount of takers input
     * @param txFee          mining fee
     * @return a data container holding the inputs, the output value and address
     * @throws TransactionVerificationException if there was an unexpected problem with the created dummy tx
     */
    public InputsAndChangeOutput takerCreatesDepositTxInputs(Transaction takeOfferFeeTx,
                                                             Coin inputAmount,
                                                             Coin txFee)
            throws TransactionVerificationException {
        // We add the mining fee 2 times to the deposit tx:
        // 1. Will be spent when publishing the deposit tx (paid by buyer)
        // 2. Will be added to the MS amount, so when publishing the payout tx the fee is already there and the outputs are not changed by fee reduction
        // The fee for the payout will be paid by the seller.

        /*
         The tx we create has that structure:

         IN[0]  input from taker fee tx > inputAmount (including tx fee) (unsigned)
         OUT[0] dummyOutputAmount (inputAmount - tx fee)

         We are only interested in the inputs.
         We get the exact input value from the taker fee tx so we don't create a change output.
         */

        // inputAmount includes the tx fee. So we subtract the fee to get the dummyOutputAmount.
        Coin dummyOutputAmount = inputAmount.subtract(txFee);

        Transaction dummyTX = new Transaction(params);
        // The output is just used to get the right inputs and change outputs, so we use an anonymous ECKey, as it will never be used for anything.
        // We don't care about fee calculation differences between the real tx and that dummy tx as we use a static tx fee.
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTX, dummyOutputAmount, LegacyAddress.fromKey(params, new ECKey()));
        dummyTX.addOutput(dummyOutput);

        // Find the needed inputs to pay the output, optionally add 1 change output.
        // Normally only 1 input and no change output is used, but we support multiple inputs and 1 change output.
        // Our spending transaction output is from the create offer fee payment.

        // We created the take offer fee tx in the structure that the second output is for the funds for the deposit tx.
        TransactionOutput reservedForTradeOutput = takeOfferFeeTx.getOutputs().get(1);
        checkArgument(reservedForTradeOutput.getValue().equals(inputAmount),
                "Reserve amount does not equal input amount");
        dummyTX.addInput(reservedForTradeOutput);

        WalletService.removeSignatures(dummyTX);
        WalletService.verifyTransaction(dummyTX);

        //WalletService.printTx("dummyTX", dummyTX);

        List<RawTransactionInput> rawTransactionInputList = dummyTX.getInputs().stream().map(e -> {
            checkNotNull(e.getConnectedOutput(), "e.getConnectedOutput() must not be null");
            checkNotNull(e.getConnectedOutput().getParentTransaction(),
                    "e.getConnectedOutput().getParentTransaction() must not be null");
            checkNotNull(e.getValue(), "e.getValue() must not be null");
            return getRawInputFromTransactionInput(e);
        }).collect(Collectors.toList());


        // TODO changeOutputValue and changeOutputAddress is not used as taker spends exact amount from fee tx.
        // Change is handled already at the fee tx creation so the handling of a change output for the deposit tx
        // can be removed here. We still keep it atm as we prefer to not introduce a larger
        // refactoring. When new trade protocol gets implemented this can be cleaned.
        // The maker though can have a change output if the taker takes less as the max. offer amount!
        return new InputsAndChangeOutput(new ArrayList<>(rawTransactionInputList), 0, null);
    }

    public PreparedDepositTxAndMakerInputs sellerAsMakerCreatesDepositTx(byte[] contractHash,
                                                                         Coin makerInputAmount,
                                                                         Coin msOutputAmount,
                                                                         List<RawTransactionInput> takerRawTransactionInputs,
                                                                         long takerChangeOutputValue,
                                                                         @Nullable String takerChangeAddressString,
                                                                         Address makerAddress,
                                                                         Address makerChangeAddress,
                                                                         byte[] buyerPubKey,
                                                                         byte[] sellerPubKey)
            throws SigningException, TransactionVerificationException, WalletException, AddressFormatException {
        return makerCreatesDepositTx(false,
                contractHash,
                makerInputAmount,
                msOutputAmount,
                takerRawTransactionInputs,
                takerChangeOutputValue,
                takerChangeAddressString,
                makerAddress,
                makerChangeAddress,
                buyerPubKey,
                sellerPubKey);
    }

    public PreparedDepositTxAndMakerInputs buyerAsMakerCreatesAndSignsDepositTx(byte[] contractHash,
                                                                                Coin makerInputAmount,
                                                                                Coin msOutputAmount,
                                                                                List<RawTransactionInput> takerRawTransactionInputs,
                                                                                long takerChangeOutputValue,
                                                                                @Nullable String takerChangeAddressString,
                                                                                Address makerAddress,
                                                                                Address makerChangeAddress,
                                                                                byte[] buyerPubKey,
                                                                                byte[] sellerPubKey)
            throws SigningException, TransactionVerificationException, WalletException, AddressFormatException {
        return makerCreatesDepositTx(true,
                contractHash,
                makerInputAmount,
                msOutputAmount,
                takerRawTransactionInputs,
                takerChangeOutputValue,
                takerChangeAddressString,
                makerAddress,
                makerChangeAddress,
                buyerPubKey,
                sellerPubKey);
    }

    /**
     * The maker creates the deposit transaction using the takers input(s) and optional output and signs his input(s).
     *
     * @param makerIsBuyer              the flag indicating if we are in the maker as buyer role or the opposite
     * @param contractHash              the hash of the contract to be added to the OP_RETURN output
     * @param makerInputAmount          the input amount of the maker
     * @param msOutputAmount            the output amount to our MS output
     * @param takerRawTransactionInputs raw data for the connected outputs for all inputs of the taker (normally 1 input)
     * @param takerChangeOutputValue    optional taker change output value
     * @param takerChangeAddressString  optional taker change address
     * @param makerAddress              the maker's address
     * @param makerChangeAddress        the maker's change address
     * @param buyerPubKey               the public key of the buyer
     * @param sellerPubKey              the public key of the seller
     * @return a data container holding the serialized transaction and the maker raw inputs
     * @throws SigningException if there was an unexpected problem signing (one of) the input(s) from the maker's wallet
     * @throws AddressFormatException if the taker base58 change address doesn't parse or its checksum is invalid
     * @throws TransactionVerificationException if there was an unexpected problem with the deposit tx or its signature(s)
     * @throws WalletException if the maker's wallet is null or there was an error choosing deposit tx input(s) from it
     */
    private PreparedDepositTxAndMakerInputs makerCreatesDepositTx(boolean makerIsBuyer,
                                                                  byte[] contractHash,
                                                                  Coin makerInputAmount,
                                                                  Coin msOutputAmount,
                                                                  List<RawTransactionInput> takerRawTransactionInputs,
                                                                  long takerChangeOutputValue,
                                                                  @Nullable String takerChangeAddressString,
                                                                  Address makerAddress,
                                                                  Address makerChangeAddress,
                                                                  byte[] buyerPubKey,
                                                                  byte[] sellerPubKey)
            throws SigningException, TransactionVerificationException, WalletException, AddressFormatException {
        checkArgument(!takerRawTransactionInputs.isEmpty());

        // First we construct a dummy TX to get the inputs and outputs we want to use for the real deposit tx.
        // Similar to the way we did in the createTakerDepositTxInputs method.
        Transaction dummyTx = new Transaction(params);
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTx, makerInputAmount, LegacyAddress.fromKey(params, new ECKey()));
        dummyTx.addOutput(dummyOutput);
        addAvailableInputsAndChangeOutputs(dummyTx, makerAddress, makerChangeAddress);
        // Normally we have only 1 input but we support multiple inputs if the user has paid in with several transactions.
        List<TransactionInput> makerInputs = dummyTx.getInputs();
        TransactionOutput makerOutput = null;

        // We don't support more than 1 optional change output
        checkArgument(dummyTx.getOutputs().size() < 3, "dummyTx.getOutputs().size() >= 3");

        // Only save change outputs, the dummy output is ignored (that's why we start with index 1)
        if (dummyTx.getOutputs().size() > 1) {
            makerOutput = dummyTx.getOutput(1);
        }

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
            // the seller's input is not signed so we attach empty script bytes
            for (RawTransactionInput rawTransactionInput : takerRawTransactionInputs)
                preparedDepositTx.addInput(getTransactionInput(preparedDepositTx, new byte[]{}, rawTransactionInput));
        } else {
            // taker is buyer role

            // Add buyer inputs
            // the seller's input is not signed so we attach empty script bytes
            for (RawTransactionInput rawTransactionInput : takerRawTransactionInputs)
                preparedDepositTx.addInput(getTransactionInput(preparedDepositTx, new byte[]{}, rawTransactionInput));

            // Add seller inputs
            for (TransactionInput input : makerInputs) {
                preparedDepositTx.addInput(input);
                makerRawTransactionInputs.add(getRawInputFromTransactionInput(input));
            }
        }


        // Add MultiSig output
        Script p2SHMultiSigOutputScript = get2of2MultiSigOutputScript(buyerPubKey, sellerPubKey);

        // Tx fee for deposit tx will be paid by buyer.
        TransactionOutput p2SHMultiSigOutput = new TransactionOutput(params, preparedDepositTx, msOutputAmount,
                p2SHMultiSigOutputScript.getProgram());
        preparedDepositTx.addOutput(p2SHMultiSigOutput);

        // We add the hash ot OP_RETURN with a 0 amount output
        TransactionOutput contractHashOutput = new TransactionOutput(params, preparedDepositTx, Coin.ZERO,
                ScriptBuilder.createOpReturnScript(contractHash).getProgram());
        preparedDepositTx.addOutput(contractHashOutput);

        TransactionOutput takerTransactionOutput = null;
        if (takerChangeOutputValue > 0 && takerChangeAddressString != null) {
            takerTransactionOutput = new TransactionOutput(params, preparedDepositTx, Coin.valueOf(takerChangeOutputValue),
                    LegacyAddress.fromBase58(params, takerChangeAddressString));
        }

        if (makerIsBuyer) {
            // Add optional buyer outputs
            if (makerOutput != null) {
                preparedDepositTx.addOutput(makerOutput);
            }

            // Add optional seller outputs
            if (takerTransactionOutput != null) {
                preparedDepositTx.addOutput(takerTransactionOutput);
            }
        } else {
            // taker is buyer role

            // Add optional seller outputs
            if (takerTransactionOutput != null) {
                preparedDepositTx.addOutput(takerTransactionOutput);
            }

            // Add optional buyer outputs
            if (makerOutput != null) {
                preparedDepositTx.addOutput(makerOutput);
            }
        }

        int start = makerIsBuyer ? 0 : takerRawTransactionInputs.size();
        int end = makerIsBuyer ? makerInputs.size() : preparedDepositTx.getInputs().size();
        for (int i = start; i < end; i++) {
            TransactionInput input = preparedDepositTx.getInput(i);
            signInput(preparedDepositTx, input, i);
            WalletService.checkScriptSig(preparedDepositTx, input, i);
        }

        WalletService.printTx("makerCreatesDepositTx", preparedDepositTx);
        WalletService.verifyTransaction(preparedDepositTx);

        return new PreparedDepositTxAndMakerInputs(makerRawTransactionInputs, preparedDepositTx.bitcoinSerialize());
    }

    /**
     * The taker signs the deposit transaction he received from the maker and publishes it.
     *
     * @param takerIsSeller             the flag indicating if we are in the taker as seller role or the opposite
     * @param contractHash              the hash of the contract to be added to the OP_RETURN output
     * @param makersDepositTxSerialized the prepared deposit transaction signed by the maker
     * @param buyerInputs               the connected outputs for all inputs of the buyer
     * @param sellerInputs              the connected outputs for all inputs of the seller
     * @param buyerPubKey               the public key of the buyer
     * @param sellerPubKey              the public key of the seller
     * @throws SigningException if (one of) the taker input(s) was of an unrecognized type for signing
     * @throws TransactionVerificationException if a maker input wasn't signed, their MultiSig script or contract hash
     * doesn't match the taker's, or there was an unexpected problem with the final deposit tx or its signatures
     * @throws WalletException if the taker's wallet is null or structurally inconsistent
     */
    public Transaction takerSignsDepositTx(boolean takerIsSeller,
                                           byte[] contractHash,
                                           byte[] makersDepositTxSerialized,
                                           List<RawTransactionInput> buyerInputs,
                                           List<RawTransactionInput> sellerInputs,
                                           byte[] buyerPubKey,
                                           byte[] sellerPubKey)
            throws SigningException, TransactionVerificationException, WalletException {
        Transaction makersDepositTx = new Transaction(params, makersDepositTxSerialized);

        checkArgument(!buyerInputs.isEmpty());
        checkArgument(!sellerInputs.isEmpty());

        // Check if maker's MultiSig script is identical to the takers
        Script p2SHMultiSigOutputScript = get2of2MultiSigOutputScript(buyerPubKey, sellerPubKey);
        if (!makersDepositTx.getOutput(0).getScriptPubKey().equals(p2SHMultiSigOutputScript)) {
            throw new TransactionVerificationException("Maker's p2SHMultiSigOutputScript does not match to takers p2SHMultiSigOutputScript");
        }

        // The outpoints are not available from the serialized makersDepositTx, so we cannot use that tx directly, but we use it to construct a new
        // depositTx
        Transaction depositTx = new Transaction(params);

        if (takerIsSeller) {
            // Add buyer inputs and apply signature
            // We grab the signature from the makersDepositTx and apply it to the new tx input
            for (int i = 0; i < buyerInputs.size(); i++) {
                TransactionInput transactionInput = makersDepositTx.getInputs().get(i);
                depositTx.addInput(getTransactionInput(depositTx, getMakersScriptSigProgram(transactionInput), buyerInputs.get(i)));
            }

            // Add seller inputs
            for (RawTransactionInput rawTransactionInput : sellerInputs) {
                depositTx.addInput(getTransactionInput(depositTx, new byte[]{}, rawTransactionInput));
            }
        } else {
            // taker is buyer
            // Add buyer inputs and apply signature
            for (RawTransactionInput rawTransactionInput : buyerInputs) {
                depositTx.addInput(getTransactionInput(depositTx, new byte[]{}, rawTransactionInput));
            }

            // Add seller inputs
            // We grab the signature from the makersDepositTx and apply it to the new tx input
            for (int i = buyerInputs.size(), k = 0; i < makersDepositTx.getInputs().size(); i++, k++) {
                TransactionInput transactionInput = makersDepositTx.getInputs().get(i);
                // We get the deposit tx unsigned if maker is seller
                depositTx.addInput(getTransactionInput(depositTx, new byte[]{}, sellerInputs.get(k)));
            }
        }

        // Check if OP_RETURN output with contract hash matches the one from the maker
        TransactionOutput contractHashOutput = new TransactionOutput(params, makersDepositTx, Coin.ZERO,
                ScriptBuilder.createOpReturnScript(contractHash).getProgram());
        log.debug("contractHashOutput {}", contractHashOutput);
        TransactionOutput makersContractHashOutput = makersDepositTx.getOutputs().get(1);
        log.debug("makersContractHashOutput {}", makersContractHashOutput);
        if (!makersContractHashOutput.getScriptPubKey().equals(contractHashOutput.getScriptPubKey())) {
            throw new TransactionVerificationException("Maker's transaction output for the contract hash is not matching takers version.");
        }

        // Add all outputs from makersDepositTx to depositTx
        makersDepositTx.getOutputs().forEach(depositTx::addOutput);
        WalletService.printTx("makersDepositTx", makersDepositTx);

        // Sign inputs
        int start = takerIsSeller ? buyerInputs.size() : 0;
        int end = takerIsSeller ? depositTx.getInputs().size() : buyerInputs.size();
        for (int i = start; i < end; i++) {
            TransactionInput input = depositTx.getInput(i);
            signInput(depositTx, input, i);
            WalletService.checkScriptSig(depositTx, input, i);
        }

        WalletService.printTx("takerSignsDepositTx", depositTx);

        WalletService.verifyTransaction(depositTx);
        WalletService.checkWalletConsistency(wallet);

        return depositTx;
    }


    public void sellerAsMakerFinalizesDepositTx(Transaction myDepositTx,
                                                Transaction takersDepositTx,
                                                int numTakersInputs)
            throws TransactionVerificationException, AddressFormatException {

        // We add takers signature from his inputs and add it to out tx which was already signed earlier.
        for (int i = 0; i < numTakersInputs; i++) {
            TransactionInput input = takersDepositTx.getInput(i);
            Script scriptSig = input.getScriptSig();
            myDepositTx.getInput(i).setScriptSig(scriptSig);
        }

        WalletService.printTx("sellerAsMakerFinalizesDepositTx", myDepositTx);
        WalletService.verifyTransaction(myDepositTx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delayed payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction createDelayedUnsignedPayoutTx(Transaction depositTx,
                                                     String donationAddressString,
                                                     Coin minerFee,
                                                     long lockTime)
            throws AddressFormatException, TransactionVerificationException {
        TransactionOutput p2SHMultiSigOutput = depositTx.getOutput(0);
        Transaction delayedPayoutTx = new Transaction(params);
        delayedPayoutTx.addInput(p2SHMultiSigOutput);
        applyLockTime(lockTime, delayedPayoutTx);
        Coin outputAmount = p2SHMultiSigOutput.getValue().subtract(minerFee);
        delayedPayoutTx.addOutput(outputAmount, LegacyAddress.fromBase58(params, donationAddressString));
        WalletService.printTx("Unsigned delayedPayoutTx ToDonationAddress", delayedPayoutTx);
        WalletService.verifyTransaction(delayedPayoutTx);
        return delayedPayoutTx;
    }

    public byte[] signDelayedPayoutTx(Transaction delayedPayoutTx,
                                      DeterministicKey myMultiSigKeyPair,
                                      byte[] buyerPubKey,
                                      byte[] sellerPubKey)
            throws AddressFormatException, TransactionVerificationException {

        Script redeemScript = get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        Sha256Hash sigHash = delayedPayoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        checkNotNull(myMultiSigKeyPair, "myMultiSigKeyPair must not be null");
        if (myMultiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }

        ECKey.ECDSASignature mySignature = myMultiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();
        WalletService.printTx("delayedPayoutTx for sig creation", delayedPayoutTx);
        WalletService.verifyTransaction(delayedPayoutTx);
        return mySignature.encodeToDER();
    }

    public Transaction finalizeDelayedPayoutTx(Transaction delayedPayoutTx,
                                               byte[] buyerPubKey,
                                               byte[] sellerPubKey,
                                               byte[] buyerSignature,
                                               byte[] sellerSignature)
            throws AddressFormatException, TransactionVerificationException, WalletException, SignatureDecodeException {
        Script redeemScript = get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        ECKey.ECDSASignature buyerECDSASignature = ECKey.ECDSASignature.decodeFromDER(buyerSignature);
        ECKey.ECDSASignature sellerECDSASignature = ECKey.ECDSASignature.decodeFromDER(sellerSignature);
        TransactionSignature buyerTxSig = new TransactionSignature(buyerECDSASignature, Transaction.SigHash.ALL, false);
        TransactionSignature sellerTxSig = new TransactionSignature(sellerECDSASignature, Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(sellerTxSig, buyerTxSig), redeemScript);
        TransactionInput input = delayedPayoutTx.getInput(0);
        input.setScriptSig(inputScript);
        WalletService.printTx("finalizeDelayedPayoutTx", delayedPayoutTx);
        WalletService.verifyTransaction(delayedPayoutTx);
        WalletService.checkWalletConsistency(wallet);
        WalletService.checkScriptSig(delayedPayoutTx, input, 0);
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        input.verify(input.getConnectedOutput());
        return delayedPayoutTx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Standard payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Seller signs payout transaction, buyer has not signed yet.
     *
     * @param depositTx                 deposit transaction
     * @param buyerPayoutAmount         payout amount for buyer
     * @param sellerPayoutAmount        payout amount for seller
     * @param buyerPayoutAddressString  address for buyer
     * @param sellerPayoutAddressString address for seller
     * @param multiSigKeyPair           DeterministicKey for MultiSig from seller
     * @param buyerPubKey               the public key of the buyer
     * @param sellerPubKey              the public key of the seller
     * @return DER encoded canonical signature
     * @throws AddressFormatException if the buyer or seller base58 address doesn't parse or its checksum is invalid
     * @throws TransactionVerificationException if there was an unexpected problem with the payout tx or its signature
     */
    public byte[] buyerSignsPayoutTx(Transaction depositTx,
                                     Coin buyerPayoutAmount,
                                     Coin sellerPayoutAmount,
                                     String buyerPayoutAddressString,
                                     String sellerPayoutAddressString,
                                     DeterministicKey multiSigKeyPair,
                                     byte[] buyerPubKey,
                                     byte[] sellerPubKey)
            throws AddressFormatException, TransactionVerificationException {
        Transaction preparedPayoutTx = createPayoutTx(depositTx, buyerPayoutAmount, sellerPayoutAmount,
                buyerPayoutAddressString, sellerPayoutAddressString);
        // MS redeemScript
        Script redeemScript = get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        // MS output from prev. tx is index 0
        Sha256Hash sigHash = preparedPayoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        checkNotNull(multiSigKeyPair, "multiSigKeyPair must not be null");
        if (multiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }
        ECKey.ECDSASignature buyerSignature = multiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();
        WalletService.printTx("prepared payoutTx", preparedPayoutTx);
        WalletService.verifyTransaction(preparedPayoutTx);
        return buyerSignature.encodeToDER();
    }


    /**
     * Seller creates and signs payout transaction and adds signature of buyer to complete the transaction.
     *
     * @param depositTx                 deposit transaction
     * @param buyerSignature            DER encoded canonical signature of buyer
     * @param buyerPayoutAmount         payout amount for buyer
     * @param sellerPayoutAmount        payout amount for seller
     * @param buyerPayoutAddressString  address for buyer
     * @param sellerPayoutAddressString address for seller
     * @param multiSigKeyPair           seller's key pair for MultiSig
     * @param buyerPubKey               the public key of the buyer
     * @param sellerPubKey              the public key of the seller
     * @return the payout transaction
     * @throws AddressFormatException if the buyer or seller base58 address doesn't parse or its checksum is invalid
     * @throws TransactionVerificationException if there was an unexpected problem with the payout tx or its signatures
     * @throws WalletException if the seller's wallet is null or structurally inconsistent
     */
    public Transaction sellerSignsAndFinalizesPayoutTx(Transaction depositTx,
                                                       byte[] buyerSignature,
                                                       Coin buyerPayoutAmount,
                                                       Coin sellerPayoutAmount,
                                                       String buyerPayoutAddressString,
                                                       String sellerPayoutAddressString,
                                                       DeterministicKey multiSigKeyPair,
                                                       byte[] buyerPubKey,
                                                       byte[] sellerPubKey)
            throws AddressFormatException, TransactionVerificationException, WalletException, SignatureDecodeException {
        Transaction payoutTx = createPayoutTx(depositTx, buyerPayoutAmount, sellerPayoutAmount, buyerPayoutAddressString, sellerPayoutAddressString);
        // MS redeemScript
        Script redeemScript = get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        // MS output from prev. tx is index 0
        Sha256Hash sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        checkNotNull(multiSigKeyPair, "multiSigKeyPair must not be null");
        if (multiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }
        ECKey.ECDSASignature sellerSignature = multiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();
        TransactionSignature buyerTxSig = new TransactionSignature(ECKey.ECDSASignature.decodeFromDER(buyerSignature),
                Transaction.SigHash.ALL, false);
        TransactionSignature sellerTxSig = new TransactionSignature(sellerSignature, Transaction.SigHash.ALL, false);
        // Take care of order of signatures. Need to be reversed here. See comment below at getMultiSigRedeemScript (seller, buyer)
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(sellerTxSig, buyerTxSig),
                redeemScript);
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
    // Mediated payoutTx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public byte[] signMediatedPayoutTx(Transaction depositTx,
                                       Coin buyerPayoutAmount,
                                       Coin sellerPayoutAmount,
                                       String buyerPayoutAddressString,
                                       String sellerPayoutAddressString,
                                       DeterministicKey myMultiSigKeyPair,
                                       byte[] buyerPubKey,
                                       byte[] sellerPubKey)
            throws AddressFormatException, TransactionVerificationException {
        Transaction preparedPayoutTx = createPayoutTx(depositTx, buyerPayoutAmount, sellerPayoutAmount, buyerPayoutAddressString, sellerPayoutAddressString);
        // MS redeemScript
        Script redeemScript = get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        // MS output from prev. tx is index 0
        Sha256Hash sigHash = preparedPayoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        checkNotNull(myMultiSigKeyPair, "myMultiSigKeyPair must not be null");
        if (myMultiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }
        ECKey.ECDSASignature mySignature = myMultiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();
        WalletService.printTx("prepared mediated payoutTx for sig creation", preparedPayoutTx);
        WalletService.verifyTransaction(preparedPayoutTx);
        return mySignature.encodeToDER();
    }

    public Transaction finalizeMediatedPayoutTx(Transaction depositTx,
                                                byte[] buyerSignature,
                                                byte[] sellerSignature,
                                                Coin buyerPayoutAmount,
                                                Coin sellerPayoutAmount,
                                                String buyerPayoutAddressString,
                                                String sellerPayoutAddressString,
                                                DeterministicKey multiSigKeyPair,
                                                byte[] buyerPubKey,
                                                byte[] sellerPubKey)
            throws AddressFormatException, TransactionVerificationException, WalletException, SignatureDecodeException {
        Transaction payoutTx = createPayoutTx(depositTx, buyerPayoutAmount, sellerPayoutAmount, buyerPayoutAddressString, sellerPayoutAddressString);
        // MS redeemScript
        Script redeemScript = get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        // MS output from prev. tx is index 0
        checkNotNull(multiSigKeyPair, "multiSigKeyPair must not be null");
        TransactionSignature buyerTxSig = new TransactionSignature(ECKey.ECDSASignature.decodeFromDER(buyerSignature),
                Transaction.SigHash.ALL, false);
        TransactionSignature sellerTxSig = new TransactionSignature(ECKey.ECDSASignature.decodeFromDER(sellerSignature),
                Transaction.SigHash.ALL, false);
        // Take care of order of signatures. Need to be reversed here. See comment below at getMultiSigRedeemScript (seller, buyer)
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(sellerTxSig, buyerTxSig), redeemScript);
        TransactionInput input = payoutTx.getInput(0);
        input.setScriptSig(inputScript);
        WalletService.printTx("mediated payoutTx", payoutTx);
        WalletService.verifyTransaction(payoutTx);
        WalletService.checkWalletConsistency(wallet);
        WalletService.checkScriptSig(payoutTx, input, 0);
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        input.verify(input.getConnectedOutput());
        return payoutTx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Arbitrated payoutTx
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO: Once we have removed legacy arbitrator from dispute domain we can remove that method as well.
    // Atm it is still used by ArbitrationManager.

    /**
     * A trader who got the signed tx from the arbitrator finalizes the payout tx.
     *
     * @param depositTxSerialized    serialized deposit tx
     * @param arbitratorSignature    DER encoded canonical signature of arbitrator
     * @param buyerPayoutAmount      payout amount of the buyer
     * @param sellerPayoutAmount     payout amount of the seller
     * @param buyerAddressString     the address of the buyer
     * @param sellerAddressString    the address of the seller
     * @param tradersMultiSigKeyPair the key pair for the MultiSig of the trader who calls that method
     * @param buyerPubKey            the public key of the buyer
     * @param sellerPubKey           the public key of the seller
     * @param arbitratorPubKey       the public key of the arbitrator
     * @return the completed payout tx
     * @throws AddressFormatException if the buyer or seller base58 address doesn't parse or its checksum is invalid
     * @throws TransactionVerificationException if there was an unexpected problem with the payout tx or its signature
     * @throws WalletException if the trade wallet is null or structurally inconsistent
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
            throws AddressFormatException, TransactionVerificationException, WalletException, SignatureDecodeException {
        Transaction depositTx = new Transaction(params, depositTxSerialized);
        TransactionOutput p2SHMultiSigOutput = depositTx.getOutput(0);
        Transaction payoutTx = new Transaction(params);
        payoutTx.addInput(p2SHMultiSigOutput);
        if (buyerPayoutAmount.isPositive()) {
            payoutTx.addOutput(buyerPayoutAmount, LegacyAddress.fromBase58(params, buyerAddressString));
        }
        if (sellerPayoutAmount.isPositive()) {
            payoutTx.addOutput(sellerPayoutAmount, LegacyAddress.fromBase58(params, sellerAddressString));
        }

        // take care of sorting!
        Script redeemScript = get2of3MultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        Sha256Hash sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        checkNotNull(tradersMultiSigKeyPair, "tradersMultiSigKeyPair must not be null");
        if (tradersMultiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }
        ECKey.ECDSASignature tradersSignature = tradersMultiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();
        TransactionSignature tradersTxSig = new TransactionSignature(tradersSignature, Transaction.SigHash.ALL, false);
        TransactionSignature arbitratorTxSig = new TransactionSignature(ECKey.ECDSASignature.decodeFromDER(arbitratorSignature),
                Transaction.SigHash.ALL, false);
        // Take care of order of signatures. See comment below at getMultiSigRedeemScript (sort order needed here: arbitrator, seller, buyer)
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(arbitratorTxSig, tradersTxSig),
                redeemScript);
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Emergency payoutTx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void emergencySignAndPublishPayoutTxFrom2of2MultiSig(String depositTxHex,
                                                                Coin buyerPayoutAmount,
                                                                Coin sellerPayoutAmount,
                                                                Coin txFee,
                                                                String buyerAddressString,
                                                                String sellerAddressString,
                                                                String buyerPrivateKeyAsHex,
                                                                String sellerPrivateKeyAsHex,
                                                                String buyerPubKeyAsHex,
                                                                String sellerPubKeyAsHex,
                                                                TxBroadcaster.Callback callback)
            throws AddressFormatException, TransactionVerificationException, WalletException {
        byte[] buyerPubKey = ECKey.fromPublicOnly(Utils.HEX.decode(buyerPubKeyAsHex)).getPubKey();
        byte[] sellerPubKey = ECKey.fromPublicOnly(Utils.HEX.decode(sellerPubKeyAsHex)).getPubKey();

        Script p2SHMultiSigOutputScript = get2of2MultiSigOutputScript(buyerPubKey, sellerPubKey);

        Coin msOutput = buyerPayoutAmount.add(sellerPayoutAmount).add(txFee);
        TransactionOutput p2SHMultiSigOutput = new TransactionOutput(params, null, msOutput, p2SHMultiSigOutputScript.getProgram());
        Transaction depositTx = new Transaction(params);
        depositTx.addOutput(p2SHMultiSigOutput);

        Transaction payoutTx = new Transaction(params);
        Sha256Hash spendTxHash = Sha256Hash.wrap(depositTxHex);
        payoutTx.addInput(new TransactionInput(params, depositTx, p2SHMultiSigOutputScript.getProgram(), new TransactionOutPoint(params, 0, spendTxHash), msOutput));

        if (buyerPayoutAmount.isPositive()) {
            payoutTx.addOutput(buyerPayoutAmount, LegacyAddress.fromBase58(params, buyerAddressString));
        }
        if (sellerPayoutAmount.isPositive()) {
            payoutTx.addOutput(sellerPayoutAmount, LegacyAddress.fromBase58(params, sellerAddressString));
        }

        // take care of sorting!
        Script redeemScript = get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        Sha256Hash sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);

        ECKey buyerPrivateKey = ECKey.fromPrivate(Utils.HEX.decode(buyerPrivateKeyAsHex));
        checkNotNull(buyerPrivateKey, "key must not be null");
        ECKey.ECDSASignature buyerECDSASignature = buyerPrivateKey.sign(sigHash, aesKey).toCanonicalised();

        ECKey sellerPrivateKey = ECKey.fromPrivate(Utils.HEX.decode(sellerPrivateKeyAsHex));
        checkNotNull(sellerPrivateKey, "key must not be null");
        ECKey.ECDSASignature sellerECDSASignature = sellerPrivateKey.sign(sigHash, aesKey).toCanonicalised();

        TransactionSignature buyerTxSig = new TransactionSignature(buyerECDSASignature, Transaction.SigHash.ALL, false);
        TransactionSignature sellerTxSig = new TransactionSignature(sellerECDSASignature, Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(sellerTxSig, buyerTxSig), redeemScript);

        TransactionInput input = payoutTx.getInput(0);
        input.setScriptSig(inputScript);
        WalletService.printTx("payoutTx", payoutTx);
        WalletService.verifyTransaction(payoutTx);
        WalletService.checkWalletConsistency(wallet);
        broadcastTx(payoutTx, callback, 20);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Broadcast tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void broadcastTx(Transaction tx, TxBroadcaster.Callback callback) {
        checkNotNull(walletConfig);
        TxBroadcaster.broadcastTx(wallet, walletConfig.peerGroup(), tx, callback);
    }

    public void broadcastTx(Transaction tx, TxBroadcaster.Callback callback, int timeoutInSec) {
        checkNotNull(walletConfig);
        TxBroadcaster.broadcastTx(wallet, walletConfig.peerGroup(), tx, callback, timeoutInSec);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the local existing wallet transaction with the given ID, or {@code null} if missing.
     *
     * @param txId the transaction ID of the transaction we want to lookup
     */
    public Transaction getWalletTx(Sha256Hash txId) {
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

    private RawTransactionInput getRawInputFromTransactionInput(@NotNull TransactionInput input) {
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        checkNotNull(input.getConnectedOutput().getParentTransaction(),
                "input.getConnectedOutput().getParentTransaction() must not be null");
        checkNotNull(input.getValue(), "input.getValue() must not be null");

        return new RawTransactionInput(input.getOutpoint().getIndex(),
                input.getConnectedOutput().getParentTransaction().bitcoinSerialize(),
                input.getValue().value);
    }

    private byte[] getMakersScriptSigProgram(TransactionInput transactionInput) throws TransactionVerificationException {
        byte[] scriptProgram = transactionInput.getScriptSig().getProgram();
        if (scriptProgram.length == 0) {
            throw new TransactionVerificationException("Inputs from maker not signed.");
        }

        return scriptProgram;
    }

    private TransactionInput getTransactionInput(Transaction depositTx,
                                                 byte[] scriptProgram,
                                                 RawTransactionInput rawTransactionInput) {
        return new TransactionInput(params, depositTx, scriptProgram, new TransactionOutPoint(params,
                rawTransactionInput.index, new Transaction(params, rawTransactionInput.parentTransaction)),
                Coin.valueOf(rawTransactionInput.value));
    }


    // TODO: Once we have removed legacy arbitrator from dispute domain we can remove that method as well.
    // Atm it is still used by traderSignAndFinalizeDisputedPayoutTx which is used by ArbitrationManager.

    // Don't use ScriptBuilder.createRedeemScript and ScriptBuilder.createP2SHOutputScript as they use a sorting
    // (Collections.sort(pubKeys, ECKey.PUBKEY_COMPARATOR);) which can lead to a non-matching list of signatures with pubKeys and the executeMultiSig does
    // not iterate all possible combinations of sig/pubKeys leading to a verification fault. That nasty bug happens just randomly as the list after sorting
    // might differ from the provided one or not.
    // Changing the while loop in executeMultiSig to fix that does not help as the reference implementation seems to behave the same (not iterating all
    // possibilities) .
    // Furthermore the executed list is reversed to the provided.
    // Best practice is to provide the list sorted by the least probable successful candidates first (arbitrator is first -> will be last in execution loop, so
    // avoiding unneeded expensive ECKey.verify calls)
    private Script get2of3MultiSigRedeemScript(byte[] buyerPubKey, byte[] sellerPubKey, byte[] arbitratorPubKey) {
        ECKey buyerKey = ECKey.fromPublicOnly(buyerPubKey);
        ECKey sellerKey = ECKey.fromPublicOnly(sellerPubKey);
        ECKey arbitratorKey = ECKey.fromPublicOnly(arbitratorPubKey);
        // Take care of sorting! Need to reverse to the order we use normally (buyer, seller, arbitrator)
        List<ECKey> keys = ImmutableList.of(arbitratorKey, sellerKey, buyerKey);
        return ScriptBuilder.createMultiSigOutputScript(2, keys);
    }

    private Script get2of2MultiSigRedeemScript(byte[] buyerPubKey, byte[] sellerPubKey) {
        ECKey buyerKey = ECKey.fromPublicOnly(buyerPubKey);
        ECKey sellerKey = ECKey.fromPublicOnly(sellerPubKey);
        // Take care of sorting! Need to reverse to the order we use normally (buyer, seller)
        List<ECKey> keys = ImmutableList.of(sellerKey, buyerKey);
        return ScriptBuilder.createMultiSigOutputScript(2, keys);
    }

    private Script get2of2MultiSigOutputScript(byte[] buyerPubKey, byte[] sellerPubKey) {
        return ScriptBuilder.createP2SHOutputScript(get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey));
    }

    private Transaction createPayoutTx(Transaction depositTx,
                                       Coin buyerPayoutAmount,
                                       Coin sellerPayoutAmount,
                                       String buyerAddressString,
                                       String sellerAddressString) throws AddressFormatException {
        TransactionOutput p2SHMultiSigOutput = depositTx.getOutput(0);
        Transaction transaction = new Transaction(params);
        transaction.addInput(p2SHMultiSigOutput);
        if (buyerPayoutAmount.isPositive()) {
            transaction.addOutput(buyerPayoutAmount, LegacyAddress.fromBase58(params, buyerAddressString));
        }
        if (sellerPayoutAmount.isPositive()) {
            transaction.addOutput(sellerPayoutAmount, LegacyAddress.fromBase58(params, sellerAddressString));
        }
        checkArgument(transaction.getOutputs().size() >= 1, "We need at least one output.");
        return transaction;
    }

    private void signInput(Transaction transaction, TransactionInput input, int inputIndex) throws SigningException {
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        Script scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        checkNotNull(wallet);
        ECKey sigKey = input.getOutpoint().getConnectedKey(wallet);
        checkNotNull(sigKey, "signInput: sigKey must not be null. input.getOutpoint()=" +
                input.getOutpoint().toString());
        if (sigKey.isEncrypted()) {
            checkNotNull(aesKey);
        }
        Sha256Hash hash = transaction.hashForSignature(inputIndex, scriptPubKey, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature signature = sigKey.sign(hash, aesKey);
        TransactionSignature txSig = new TransactionSignature(signature, Transaction.SigHash.ALL, false);
        if (ScriptPattern.isP2PK(scriptPubKey)) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig));
        } else if (ScriptPattern.isP2PKH(scriptPubKey)) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig, sigKey));
        } else {
            throw new SigningException("Don't know how to sign for this kind of scriptPubKey: " + scriptPubKey);
        }
    }

    private void addAvailableInputsAndChangeOutputs(Transaction transaction,
                                                    Address address,
                                                    Address changeAddress) throws WalletException {
        SendRequest sendRequest = null;
        try {
            // Let the framework do the work to find the right inputs
            sendRequest = SendRequest.forTx(transaction);
            sendRequest.shuffleOutputs = false;
            sendRequest.aesKey = aesKey;
            // We use a fixed fee
            sendRequest.fee = Coin.ZERO;
            sendRequest.feePerKb = Coin.ZERO;
            sendRequest.ensureMinRequiredFee = false;
            // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to wait for 1 confirmation)
            sendRequest.coinSelector = new BtcCoinSelector(address, preferences.getIgnoreDustThreshold());
            // We use always the same address in a trade for all transactions
            sendRequest.changeAddress = changeAddress;
            // With the usage of completeTx() we get all the work done with fee calculation, validation and coin selection.
            // We don't commit that tx to the wallet as it will be changed later and it's not signed yet.
            // So it will not change the wallet balance.
            checkNotNull(wallet, "wallet must not be null");
            wallet.completeTx(sendRequest);
        } catch (Throwable t) {
            if (sendRequest != null && sendRequest.tx != null) {
                log.warn("addAvailableInputsAndChangeOutputs: sendRequest.tx={}, sendRequest.tx.getOutputs()={}",
                        sendRequest.tx, sendRequest.tx.getOutputs());
            }

            throw new WalletException(t);
        }
    }

    private void applyLockTime(long lockTime, Transaction tx) {
        checkArgument(!tx.getInputs().isEmpty(), "The tx must have inputs. tx={}", tx);
        tx.getInputs().forEach(input -> input.setSequenceNumber(TransactionInput.NO_SEQUENCE - 1));
        tx.setLockTime(lockTime);
    }

    // BISQ issue #4039: prevent dust outputs from being created.
    // check all the outputs in a proposed transaction, if any are below the dust threshold
    // remove them, noting the details in the log. returns 'true' to indicate if any dust was
    // removed.
    private boolean removeDust(Transaction transaction) {
        List<TransactionOutput> originalTransactionOutputs = transaction.getOutputs();
        List<TransactionOutput> keepTransactionOutputs = new ArrayList<>();
        for (TransactionOutput transactionOutput : originalTransactionOutputs) {
            if (transactionOutput.getValue().isLessThan(Restrictions.getMinNonDustOutput())) {
                log.info("your transaction would have contained a dust output of {}", transactionOutput.toString());
            } else {
                keepTransactionOutputs.add(transactionOutput);
            }
        }
        // if dust was detected, keepTransactionOutputs will have fewer elements than originalTransactionOutputs
        // set the transaction outputs to what we saved in keepTransactionOutputs, thus discarding dust.
        if (keepTransactionOutputs.size() != originalTransactionOutputs.size()) {
            log.info("dust output was detected and removed, the new output is as follows:");
            transaction.clearOutputs();
            for (TransactionOutput transactionOutput : keepTransactionOutputs) {
                transaction.addOutput(transactionOutput);
                log.info("{}", transactionOutput.toString());
            }
            return true;    // dust was removed
        }
        return false;       // no action necessary
    }
}
