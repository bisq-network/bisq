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
import bisq.core.crypto.LowRSigningKey;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import bisq.common.config.Config;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
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

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class TradeWalletService {
    private static final Logger log = LoggerFactory.getLogger(TradeWalletService.class);
    public static final Coin MIN_DELAYED_PAYOUT_TX_FEE = Coin.valueOf(1000);

    private final WalletsSetup walletsSetup;
    private final Preferences preferences;
    private final NetworkParameters params;

    private final WarningTransactionFactory warningTransactionFactory;
    private final RedirectionTransactionFactory redirectionTransactionFactory;

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
        this.warningTransactionFactory = new WarningTransactionFactory(params);
        this.redirectionTransactionFactory = new RedirectionTransactionFactory(params);
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
            tradingFeeTx.addOutput(tradingFee, Address.fromString(params, feeReceiverAddress));
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
                log.error("Balance = {}; CoinSelector = {}", wallet.getBalance(sendRequest.coinSelector), sendRequest.coinSelector);
            }
            log.error("createBtcTradingFeeTx failed: tradingFeeTx={}, txOutputs={}", tradingFeeTx.toString(),
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
        try {
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
        } catch (Throwable t) {
            log.error("completeBsqTradingFeeTx: preparedBsqTx={}", preparedBsqTx.toString());
            throw t;
        }
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
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTX, dummyOutputAmount, SegwitAddress.fromKey(params, new ECKey()));
        dummyTX.addOutput(dummyOutput);

        // Find the needed inputs to pay the output, optionally add 1 change output.
        // Normally only 1 input and no change output is used, but we support multiple inputs and 1 change output.
        // Our spending transaction output is from the create offer fee payment.

        // We created the take offer fee tx in the structure that the second output is for the funds for the deposit tx.
        TransactionOutput reservedForTradeOutput = takeOfferFeeTx.getOutputs().get(1);
        checkArgument(reservedForTradeOutput.getValue().equals(inputAmount),
                "Reserve amount does not equal input amount");
        dummyTX.addInput(reservedForTradeOutput);

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

    public PreparedDepositTxAndMakerInputs sellerAsMakerCreatesDepositTx(Coin makerInputAmount,
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

    public PreparedDepositTxAndMakerInputs buyerAsMakerCreatesAndSignsDepositTx(Coin makerInputAmount,
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
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTx, makerInputAmount, SegwitAddress.fromKey(params, new ECKey()));
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
        Script hashedMultiSigOutputScript = get2of2MultiSigOutputScript(buyerPubKey, sellerPubKey, false);

        // Tx fee for deposit tx will be paid by buyer.
        TransactionOutput hashedMultiSigOutput = new TransactionOutput(params, preparedDepositTx, msOutputAmount,
                hashedMultiSigOutputScript.getProgram());
        preparedDepositTx.addOutput(hashedMultiSigOutput);

        TransactionOutput takerTransactionOutput = null;
        if (takerChangeOutputValue > 0 && takerChangeAddressString != null) {
            takerTransactionOutput = new TransactionOutput(params, preparedDepositTx, Coin.valueOf(takerChangeOutputValue),
                    Address.fromString(params, takerChangeAddressString));
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
     * @param makersDepositTxSerialized the prepared deposit transaction signed by the maker
     * @param msOutputAmount            the MultiSig output amount, as determined by the taker
     * @param buyerInputs               the connected outputs for all inputs of the buyer
     * @param sellerInputs              the connected outputs for all inputs of the seller
     * @param buyerPubKey               the public key of the buyer
     * @param sellerPubKey              the public key of the seller
     * @throws SigningException if (one of) the taker input(s) was of an unrecognized type for signing
     * @throws TransactionVerificationException if a non-P2WH maker-as-buyer input wasn't signed, the maker's MultiSig
     * script, contract hash or output amount doesn't match the taker's, or there was an unexpected problem with the
     * final deposit tx or its signatures
     * @throws WalletException if the taker's wallet is null or structurally inconsistent
     */
    public Transaction takerSignsDepositTx(boolean takerIsSeller,
                                           byte[] makersDepositTxSerialized,
                                           Coin msOutputAmount,
                                           List<RawTransactionInput> buyerInputs,
                                           List<RawTransactionInput> sellerInputs,
                                           byte[] buyerPubKey,
                                           byte[] sellerPubKey)
            throws SigningException, TransactionVerificationException, WalletException {
        Transaction makersDepositTx = new Transaction(params, makersDepositTxSerialized);

        checkArgument(!buyerInputs.isEmpty());
        checkArgument(!sellerInputs.isEmpty());

        // Check if maker's MultiSig script is identical to the taker's
        Script hashedMultiSigOutputScript = get2of2MultiSigOutputScript(buyerPubKey, sellerPubKey, false);
        if (!makersDepositTx.getOutput(0).getScriptPubKey().equals(hashedMultiSigOutputScript)) {
            throw new TransactionVerificationException("Maker's hashedMultiSigOutputScript does not match taker's hashedMultiSigOutputScript");
        }

        // Check if maker's MultiSig output value is identical to the taker's
        if (!makersDepositTx.getOutput(0).getValue().equals(msOutputAmount)) {
            throw new TransactionVerificationException("Maker's MultiSig output amount does not match taker's MultiSig output amount");
        }

        // The outpoints are not available from the serialized makersDepositTx, so we cannot use that tx directly, but we use it to construct a new
        // depositTx
        Transaction depositTx = new Transaction(params);

        if (takerIsSeller) {
            // Add buyer inputs and apply signature
            // We grab the signature from the makersDepositTx and apply it to the new tx input
            for (int i = 0; i < buyerInputs.size(); i++) {
                TransactionInput makersInput = makersDepositTx.getInputs().get(i);
                byte[] makersScriptSigProgram = makersInput.getScriptSig().getProgram();
                TransactionInput input = getTransactionInput(depositTx, makersScriptSigProgram, buyerInputs.get(i));
                Script scriptPubKey = checkNotNull(input.getConnectedOutput()).getScriptPubKey();
                if (makersScriptSigProgram.length == 0 && !ScriptPattern.isP2WH(scriptPubKey)) {
                    throw new TransactionVerificationException("Non-segwit inputs from maker not signed.");
                }
                if (!TransactionWitness.EMPTY.equals(makersInput.getWitness())) {
                    input.setWitness(makersInput.getWitness());
                }
                depositTx.addInput(input);
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
            TransactionInput takersInput = takersDepositTx.getInput(i);
            Script takersScriptSig = takersInput.getScriptSig();
            TransactionInput txInput = myDepositTx.getInput(i);
            txInput.setScriptSig(takersScriptSig);
            TransactionWitness witness = takersInput.getWitness();
            if (!TransactionWitness.EMPTY.equals(witness)) {
                txInput.setWitness(witness);
            }
        }

        WalletService.printTx("sellerAsMakerFinalizesDepositTx", myDepositTx);
        WalletService.verifyTransaction(myDepositTx);
    }


    public void sellerAddsBuyerWitnessesToDepositTx(Transaction myDepositTx,
                                                    Transaction buyersDepositTxWithWitnesses) {
        int numberInputs = myDepositTx.getInputs().size();
        for (int i = 0; i < numberInputs; i++) {
            var txInput = myDepositTx.getInput(i);
            var witnessFromBuyer = buyersDepositTxWithWitnesses.getInput(i).getWitness();

            if (TransactionWitness.EMPTY.equals(txInput.getWitness()) &&
                    !TransactionWitness.EMPTY.equals(witnessFromBuyer)) {
                txInput.setWitness(witnessFromBuyer);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delayed payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction createDelayedUnsignedPayoutTx(Transaction depositTx,
                                                     List<Tuple2<Long, String>> receivers,
                                                     long lockTime)
            throws AddressFormatException, TransactionVerificationException {
        TransactionOutput depositTxOutput = depositTx.getOutput(0);
        Transaction delayedPayoutTx = new Transaction(params);
        delayedPayoutTx.addInput(depositTxOutput);
        applyLockTime(lockTime, delayedPayoutTx);
        checkArgument(!receivers.isEmpty(), "receivers must not be empty");
        receivers.forEach(receiver -> delayedPayoutTx.addOutput(Coin.valueOf(receiver.first), Address.fromString(params, receiver.second)));
        WalletService.printTx("Unsigned delayedPayoutTx ToDonationAddress", delayedPayoutTx);
        WalletService.verifyTransaction(delayedPayoutTx);
        return delayedPayoutTx;
    }

    public Transaction createDelayedUnsignedPayoutTx(Transaction depositTx,
                                                     String donationAddressString,
                                                     Coin minerFee,
                                                     long lockTime)
            throws AddressFormatException, TransactionVerificationException {
        TransactionOutput depositTxOutput = depositTx.getOutput(0);
        Transaction delayedPayoutTx = new Transaction(params);
        delayedPayoutTx.addInput(depositTxOutput);
        applyLockTime(lockTime, delayedPayoutTx);
        Coin outputAmount = depositTxOutput.getValue().subtract(minerFee);
        delayedPayoutTx.addOutput(outputAmount, Address.fromString(params, donationAddressString));
        WalletService.printTx("Unsigned delayedPayoutTx ToDonationAddress", delayedPayoutTx);
        WalletService.verifyTransaction(delayedPayoutTx);
        return delayedPayoutTx;
    }

    public byte[] signDelayedPayoutTx(Transaction delayedPayoutTx,
                                      Transaction preparedDepositTx,
                                      DeterministicKey myMultiSigKeyPair,
                                      byte[] buyerPubKey,
                                      byte[] sellerPubKey)
            throws AddressFormatException, TransactionVerificationException {

        Script redeemScript = get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        Sha256Hash sigHash;
        Coin delayedPayoutTxInputValue = preparedDepositTx.getOutput(0).getValue();
        sigHash = delayedPayoutTx.hashForWitnessSignature(0, redeemScript,
                delayedPayoutTxInputValue, Transaction.SigHash.ALL, false);
        checkNotNull(myMultiSigKeyPair, "myMultiSigKeyPair must not be null");
        if (myMultiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }

        ECKey.ECDSASignature mySignature = LowRSigningKey.from(myMultiSigKeyPair).sign(sigHash, aesKey);
        WalletService.printTx("delayedPayoutTx for sig creation", delayedPayoutTx);
        WalletService.verifyTransaction(delayedPayoutTx);
        return mySignature.encodeToDER();
    }

    public Transaction finalizeUnconnectedDelayedPayoutTx(Transaction delayedPayoutTx,
                                                          byte[] buyerPubKey,
                                                          byte[] sellerPubKey,
                                                          byte[] buyerSignature,
                                                          byte[] sellerSignature,
                                                          Coin inputValue)
            throws AddressFormatException, TransactionVerificationException, SignatureDecodeException {

        Script redeemScript = get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        ECKey.ECDSASignature buyerECDSASignature = ECKey.ECDSASignature.decodeFromDER(buyerSignature);
        ECKey.ECDSASignature sellerECDSASignature = ECKey.ECDSASignature.decodeFromDER(sellerSignature);
        TransactionSignature buyerTxSig = new TransactionSignature(buyerECDSASignature, Transaction.SigHash.ALL, false);
        TransactionSignature sellerTxSig = new TransactionSignature(sellerECDSASignature, Transaction.SigHash.ALL, false);
        TransactionInput input = delayedPayoutTx.getInput(0);
        input.setScriptSig(ScriptBuilder.createEmpty());
        TransactionWitness witness = TransactionWitness.redeemP2WSH(redeemScript, sellerTxSig, buyerTxSig);
        input.setWitness(witness);
        WalletService.printTx("finalizeDelayedPayoutTx", delayedPayoutTx);
        WalletService.verifyTransaction(delayedPayoutTx);

        if (checkNotNull(inputValue).isLessThan(delayedPayoutTx.getOutputSum().add(MIN_DELAYED_PAYOUT_TX_FEE))) {
            throw new TransactionVerificationException("Delayed payout tx is paying less than the minimum allowed tx fee");
        }
        Script scriptPubKey = get2of2MultiSigOutputScript(buyerPubKey, sellerPubKey, false);
        input.getScriptSig().correctlySpends(delayedPayoutTx, 0, witness, inputValue, scriptPubKey, Script.ALL_VERIFY_FLAGS);
        return delayedPayoutTx;
    }

    public Transaction finalizeDelayedPayoutTx(Transaction delayedPayoutTx,
                                               byte[] buyerPubKey,
                                               byte[] sellerPubKey,
                                               byte[] buyerSignature,
                                               byte[] sellerSignature)
            throws AddressFormatException, TransactionVerificationException, WalletException, SignatureDecodeException {

        TransactionInput input = delayedPayoutTx.getInput(0);
        finalizeUnconnectedDelayedPayoutTx(delayedPayoutTx, buyerPubKey, sellerPubKey, buyerSignature, sellerSignature, input.getValue());

        WalletService.checkWalletConsistency(wallet);
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        input.verify(input.getConnectedOutput());
        return delayedPayoutTx;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction createUnsignedWarningTx(boolean isBuyer,
                                               TransactionOutput depositTxOutput,
                                               long lockTime,
                                               byte[] buyerPubKey,
                                               byte[] sellerPubKey,
                                               long claimDelay,
                                               long miningFee,
                                               Tuple2<Long, String> feeBumpOutputAmountAndAddress)
            throws TransactionVerificationException {
        return warningTransactionFactory.createUnsignedWarningTransaction(
                isBuyer,
                depositTxOutput,
                lockTime,
                buyerPubKey,
                sellerPubKey,
                claimDelay,
                miningFee,
                feeBumpOutputAmountAndAddress
        );
    }

    public byte[] signWarningTx(Transaction warningTx,
                                TransactionOutput depositTxOutput,
                                DeterministicKey myMultiSigKeyPair,
                                byte[] buyerPubKey,
                                byte[] sellerPubKey)
            throws AddressFormatException, TransactionVerificationException {
        return warningTransactionFactory.signWarningTransaction(
                warningTx,
                depositTxOutput,
                myMultiSigKeyPair,
                buyerPubKey,
                sellerPubKey,
                aesKey
        );
    }

    public Transaction finalizeWarningTx(Transaction warningTx,
                                         byte[] buyerPubKey,
                                         byte[] sellerPubKey,
                                         byte[] buyerSignature,
                                         byte[] sellerSignature,
                                         Coin inputValue)
            throws AddressFormatException, TransactionVerificationException, SignatureDecodeException {
        return warningTransactionFactory.finalizeWarningTransaction(
                warningTx,
                buyerPubKey,
                sellerPubKey,
                buyerSignature,
                sellerSignature,
                inputValue
        );
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Redirection tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction createUnsignedRedirectionTx(TransactionOutput warningTxOutput,
                                                   List<Tuple2<Long, String>> receivers,
                                                   Tuple2<Long, String> feeBumpOutputAmountAndAddress)
            throws AddressFormatException, TransactionVerificationException {
        return redirectionTransactionFactory.createUnsignedRedirectionTransaction(
                warningTxOutput,
                receivers,
                feeBumpOutputAmountAndAddress
        );
    }

    public byte[] signRedirectionTx(Transaction redirectionTx,
                                    TransactionOutput warningTxOutput,
                                    DeterministicKey myMultiSigKeyPair)
            throws AddressFormatException, TransactionVerificationException {
        return redirectionTransactionFactory.signRedirectionTransaction(
                redirectionTx,
                warningTxOutput,
                myMultiSigKeyPair,
                aesKey
        );
    }

    public Transaction finalizeRedirectionTx(Transaction warningTx,
                                             Transaction redirectionTx,
                                             byte[] buyerSignature,
                                             byte[] sellerSignature,
                                             Coin inputValue)
            throws AddressFormatException, TransactionVerificationException, SignatureDecodeException {
        return redirectionTransactionFactory.finalizeRedirectionTransaction(
                warningTx,
                redirectionTx,
                buyerSignature,
                sellerSignature,
                inputValue
        );
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Claim tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction createSignedClaimTx(Transaction warningTx,
                                           long nSequence,
                                           Address payoutAddress,
                                           long miningFee,
                                           DeterministicKey myMultiSigKeyPair,
                                           KeyParameter aesKey) throws TransactionVerificationException {
        return new ClaimTransactionFactory(params)
                .createSignedClaimTransaction(warningTx, nSequence, payoutAddress, miningFee, myMultiSigKeyPair, aesKey);
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
        Sha256Hash sigHash;
        TransactionOutput hashedMultiSigOutput = depositTx.getOutput(0);
        if (ScriptPattern.isP2SH(hashedMultiSigOutput.getScriptPubKey())) {
            sigHash = preparedPayoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        } else {
            Coin inputValue = hashedMultiSigOutput.getValue();
            sigHash = preparedPayoutTx.hashForWitnessSignature(0, redeemScript,
                    inputValue, Transaction.SigHash.ALL, false);
        }
        checkNotNull(multiSigKeyPair, "multiSigKeyPair must not be null");
        if (multiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }
        ECKey.ECDSASignature buyerSignature = LowRSigningKey.from(multiSigKeyPair).sign(sigHash, aesKey);
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
        TransactionOutput hashedMultiSigOutput = depositTx.getOutput(0);
        boolean hashedMultiSigOutputIsLegacy = ScriptPattern.isP2SH(hashedMultiSigOutput.getScriptPubKey());
        Sha256Hash sigHash;
        if (hashedMultiSigOutputIsLegacy) {
            sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        } else {
            Coin inputValue = hashedMultiSigOutput.getValue();
            sigHash = payoutTx.hashForWitnessSignature(0, redeemScript,
                    inputValue, Transaction.SigHash.ALL, false);
        }
        checkNotNull(multiSigKeyPair, "multiSigKeyPair must not be null");
        if (multiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }
        ECKey.ECDSASignature sellerSignature = LowRSigningKey.from(multiSigKeyPair).sign(sigHash, aesKey);
        TransactionSignature buyerTxSig = new TransactionSignature(ECKey.ECDSASignature.decodeFromDER(buyerSignature),
                Transaction.SigHash.ALL, false);
        TransactionSignature sellerTxSig = new TransactionSignature(sellerSignature, Transaction.SigHash.ALL, false);
        // Take care of order of signatures. Need to be reversed here. See comment below at getMultiSigRedeemScript (seller, buyer)
        TransactionInput input = payoutTx.getInput(0);
        if (hashedMultiSigOutputIsLegacy) {
            Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(sellerTxSig, buyerTxSig),
                    redeemScript);
            input.setScriptSig(inputScript);
        } else {
            input.setScriptSig(ScriptBuilder.createEmpty());
            input.setWitness(TransactionWitness.redeemP2WSH(redeemScript, sellerTxSig, buyerTxSig));
        }
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
        TransactionOutput hashedMultiSigOutput = depositTx.getOutput(0);
        boolean hashedMultiSigOutputIsLegacy = ScriptPattern.isP2SH(hashedMultiSigOutput.getScriptPubKey());
        Sha256Hash sigHash;
        if (hashedMultiSigOutputIsLegacy) {
            sigHash = preparedPayoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        } else {
            Coin inputValue = hashedMultiSigOutput.getValue();
            sigHash = preparedPayoutTx.hashForWitnessSignature(0, redeemScript,
                    inputValue, Transaction.SigHash.ALL, false);
        }
        checkNotNull(myMultiSigKeyPair, "myMultiSigKeyPair must not be null");
        if (myMultiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }
        ECKey.ECDSASignature mySignature = LowRSigningKey.from(myMultiSigKeyPair).sign(sigHash, aesKey);
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
        TransactionOutput hashedMultiSigOutput = depositTx.getOutput(0);
        boolean hashedMultiSigOutputIsLegacy = ScriptPattern.isP2SH(hashedMultiSigOutput.getScriptPubKey());
        TransactionInput input = payoutTx.getInput(0);
        if (hashedMultiSigOutputIsLegacy) {
            Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(sellerTxSig, buyerTxSig),
                    redeemScript);
            input.setScriptSig(inputScript);
        } else {
            input.setScriptSig(ScriptBuilder.createEmpty());
            input.setWitness(TransactionWitness.redeemP2WSH(redeemScript, sellerTxSig, buyerTxSig));
        }
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
        TransactionOutput hashedMultiSigOutput = depositTx.getOutput(0);
        Transaction payoutTx = new Transaction(params);
        payoutTx.addInput(hashedMultiSigOutput);
        if (buyerPayoutAmount.isPositive()) {
            payoutTx.addOutput(buyerPayoutAmount, Address.fromString(params, buyerAddressString));
        }
        if (sellerPayoutAmount.isPositive()) {
            payoutTx.addOutput(sellerPayoutAmount, Address.fromString(params, sellerAddressString));
        }

        // take care of sorting!
        Script redeemScript = get2of3MultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        Sha256Hash sigHash;
        boolean hashedMultiSigOutputIsLegacy = !ScriptPattern.isP2SH(hashedMultiSigOutput.getScriptPubKey());
        if (hashedMultiSigOutputIsLegacy) {
            sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        } else {
            Coin inputValue = hashedMultiSigOutput.getValue();
            sigHash = payoutTx.hashForWitnessSignature(0, redeemScript,
                    inputValue, Transaction.SigHash.ALL, false);
        }
        checkNotNull(tradersMultiSigKeyPair, "tradersMultiSigKeyPair must not be null");
        if (tradersMultiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }
        ECKey.ECDSASignature tradersSignature = LowRSigningKey.from(tradersMultiSigKeyPair).sign(sigHash, aesKey);
        TransactionSignature tradersTxSig = new TransactionSignature(tradersSignature, Transaction.SigHash.ALL, false);
        TransactionSignature arbitratorTxSig = new TransactionSignature(ECKey.ECDSASignature.decodeFromDER(arbitratorSignature),
                Transaction.SigHash.ALL, false);
        TransactionInput input = payoutTx.getInput(0);
        // Take care of order of signatures. See comment below at getMultiSigRedeemScript (sort order needed here: arbitrator, seller, buyer)
        if (hashedMultiSigOutputIsLegacy) {
            Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(
                    ImmutableList.of(arbitratorTxSig, tradersTxSig),
                    redeemScript);
            input.setScriptSig(inputScript);
        } else {
            input.setScriptSig(ScriptBuilder.createEmpty());
            input.setWitness(TransactionWitness.redeemP2WSH(redeemScript, arbitratorTxSig, tradersTxSig));
        }
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

    public Tuple2<String, String> emergencyBuildPayoutTxFrom2of2MultiSig(String depositTxHex,
                                                                         Coin buyerPayoutAmount,
                                                                         Coin sellerPayoutAmount,
                                                                         Coin txFee,
                                                                         String buyerAddressString,
                                                                         String sellerAddressString,
                                                                         String buyerPubKeyAsHex,
                                                                         String sellerPubKeyAsHex,
                                                                         boolean hashedMultiSigOutputIsLegacy) {
        byte[] buyerPubKey = ECKey.fromPublicOnly(Utils.HEX.decode(buyerPubKeyAsHex)).getPubKey();
        byte[] sellerPubKey = ECKey.fromPublicOnly(Utils.HEX.decode(sellerPubKeyAsHex)).getPubKey();
        Script redeemScript = get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        Coin msOutputValue = buyerPayoutAmount.add(sellerPayoutAmount).add(txFee);
        Transaction payoutTx = new Transaction(params);
        Sha256Hash spendTxHash = Sha256Hash.wrap(depositTxHex);
        payoutTx.addInput(new TransactionInput(params, payoutTx, new byte[]{}, new TransactionOutPoint(params, 0, spendTxHash), msOutputValue));

        if (buyerPayoutAmount.isPositive()) {
            payoutTx.addOutput(buyerPayoutAmount, Address.fromString(params, buyerAddressString));
        }
        if (sellerPayoutAmount.isPositive()) {
            payoutTx.addOutput(sellerPayoutAmount, Address.fromString(params, sellerAddressString));
        }

        String redeemScriptHex = Utils.HEX.encode(redeemScript.getProgram());
        String unsignedTxHex = Utils.HEX.encode(payoutTx.bitcoinSerialize(!hashedMultiSigOutputIsLegacy));
        return new Tuple2<>(redeemScriptHex, unsignedTxHex);
    }

    public String emergencyGenerateSignature(String rawTxHex,
                                             String redeemScriptHex,
                                             Coin inputValue,
                                             String myPrivKeyAsHex)
            throws IllegalArgumentException {
        boolean hashedMultiSigOutputIsLegacy = true;
        if (rawTxHex.startsWith("010000000001"))
            hashedMultiSigOutputIsLegacy = false;
        byte[] payload = Utils.HEX.decode(rawTxHex);
        Transaction payoutTx = new Transaction(params, payload, null, params.getDefaultSerializer(), payload.length);
        Script redeemScript = new Script(Utils.HEX.decode(redeemScriptHex));
        Sha256Hash sigHash;
        if (hashedMultiSigOutputIsLegacy) {
            sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        } else {
            sigHash = payoutTx.hashForWitnessSignature(0, redeemScript,
                    inputValue, Transaction.SigHash.ALL, false);
        }

        ECKey myPrivateKey = ECKey.fromPrivate(Utils.HEX.decode(myPrivKeyAsHex));
        checkNotNull(myPrivateKey, "key must not be null");
        ECKey.ECDSASignature myECDSASignature = LowRSigningKey.from(myPrivateKey).sign(sigHash, aesKey);
        TransactionSignature myTxSig = new TransactionSignature(myECDSASignature, Transaction.SigHash.ALL, false);
        return Utils.HEX.encode(myTxSig.encodeToBitcoin());
    }

    public Tuple2<String, String> emergencyApplySignatureToPayoutTxFrom2of2MultiSig(String unsignedTxHex,
                                                                                    String redeemScriptHex,
                                                                                    String buyerSignatureAsHex,
                                                                                    String sellerSignatureAsHex,
                                                                                    boolean hashedMultiSigOutputIsLegacy)
            throws AddressFormatException, SignatureDecodeException {
        Transaction payoutTx = new Transaction(params, Utils.HEX.decode(unsignedTxHex));
        TransactionSignature buyerTxSig = TransactionSignature.decodeFromBitcoin(Utils.HEX.decode(buyerSignatureAsHex), true, true);
        TransactionSignature sellerTxSig = TransactionSignature.decodeFromBitcoin(Utils.HEX.decode(sellerSignatureAsHex), true, true);
        Script redeemScript = new Script(Utils.HEX.decode(redeemScriptHex));

        TransactionInput input = payoutTx.getInput(0);
        if (hashedMultiSigOutputIsLegacy) {
            Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(sellerTxSig, buyerTxSig),
                    redeemScript);
            input.setScriptSig(inputScript);
        } else {
            input.setScriptSig(ScriptBuilder.createEmpty());
            input.setWitness(TransactionWitness.redeemP2WSH(redeemScript, sellerTxSig, buyerTxSig));
        }
        String txId = payoutTx.getTxId().toString();
        String signedTxHex = Utils.HEX.encode(payoutTx.bitcoinSerialize(!hashedMultiSigOutputIsLegacy));
        return new Tuple2<>(txId, signedTxHex);
    }

    public void emergencyPublishPayoutTxFrom2of2MultiSig(String signedTxHex, TxBroadcaster.Callback callback)
            throws AddressFormatException, TransactionVerificationException, WalletException {
        Transaction payoutTx = new Transaction(params, Utils.HEX.decode(signedTxHex));
        WalletService.printTx("payoutTx", payoutTx);
        WalletService.verifyTransaction(payoutTx);
        WalletService.checkWalletConsistency(wallet);
        broadcastTx(payoutTx, callback, 20);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqSwap tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction sellerBuildBsqSwapTx(List<RawTransactionInput> buyersBsqInputs,
                                            List<RawTransactionInput> sellersBtcInputs,
                                            Coin sellersBsqPayoutAmount,
                                            String sellersBsqPayoutAddress,
                                            @Nullable Coin buyersBsqChangeAmount,
                                            @Nullable String buyersBsqChangeAddress,
                                            Coin buyersBtcPayoutAmount,
                                            String buyersBtcPayoutAddress,
                                            @Nullable Coin sellersBtcChangeAmount,
                                            @Nullable String sellersBtcChangeAddress) throws AddressFormatException {

        Transaction transaction = new Transaction(params);
        List<TransactionInput> sellersBtcTransactionInput = sellersBtcInputs.stream()
                .map(rawInput -> getTransactionInput(transaction, new byte[]{}, rawInput))
                .collect(Collectors.toList());
        return buildBsqSwapTx(buyersBsqInputs,
                sellersBtcTransactionInput,
                sellersBsqPayoutAmount,
                sellersBsqPayoutAddress,
                buyersBsqChangeAmount,
                buyersBsqChangeAddress,
                buyersBtcPayoutAmount,
                buyersBtcPayoutAddress,
                sellersBtcChangeAmount,
                sellersBtcChangeAddress,
                transaction);
    }

    public Transaction buyerBuildBsqSwapTx(List<RawTransactionInput> buyersBsqInputs,
                                           List<TransactionInput> sellersBtcInputs,
                                           Coin sellersBsqPayoutAmount,
                                           String sellersBsqPayoutAddress,
                                           @Nullable Coin buyersBsqChangeAmount,
                                           @Nullable String buyersBsqChangeAddress,
                                           Coin buyersBtcPayoutAmount,
                                           String buyersBtcPayoutAddress,
                                           @Nullable Coin sellersBtcChangeAmount,
                                           @Nullable String sellersBtcChangeAddress) throws AddressFormatException {
        Transaction transaction = new Transaction(params);
        return buildBsqSwapTx(buyersBsqInputs,
                sellersBtcInputs,
                sellersBsqPayoutAmount,
                sellersBsqPayoutAddress,
                buyersBsqChangeAmount,
                buyersBsqChangeAddress,
                buyersBtcPayoutAmount,
                buyersBtcPayoutAddress,
                sellersBtcChangeAmount,
                sellersBtcChangeAddress,
                transaction);
    }

    private Transaction buildBsqSwapTx(List<RawTransactionInput> buyersBsqInputs,
                                       List<TransactionInput> sellersBtcInputs,
                                       Coin sellersBsqPayoutAmount,
                                       String sellersBsqPayoutAddress,
                                       @Nullable Coin buyersBsqChangeAmount,
                                       @Nullable String buyersBsqChangeAddress,
                                       Coin buyersBtcPayoutAmount,
                                       String buyersBtcPayoutAddress,
                                       @Nullable Coin sellersBtcChangeAmount,
                                       @Nullable String sellersBtcChangeAddress,
                                       Transaction transaction) throws AddressFormatException {

        buyersBsqInputs.forEach(rawInput -> transaction.addInput(getTransactionInput(transaction, new byte[]{}, rawInput)));
        sellersBtcInputs.forEach(transaction::addInput);

        transaction.addOutput(sellersBsqPayoutAmount, Address.fromString(params, sellersBsqPayoutAddress));

        if (buyersBsqChangeAmount != null && buyersBsqChangeAmount.isPositive())
            transaction.addOutput(buyersBsqChangeAmount, Address.fromString(params, Objects.requireNonNull(buyersBsqChangeAddress)));

        transaction.addOutput(buyersBtcPayoutAmount, Address.fromString(params, buyersBtcPayoutAddress));

        if (sellersBtcChangeAmount != null && sellersBtcChangeAmount.isPositive())
            transaction.addOutput(sellersBtcChangeAmount, Address.fromString(params, Objects.requireNonNull(sellersBtcChangeAddress)));

        return transaction;
    }

    public void signBsqSwapTransaction(Transaction transaction, List<TransactionInput> myInputs)
            throws SigningException {
        for (TransactionInput input : myInputs) {
            signInput(transaction, input, input.getIndex());
        }
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

    // This method might be replace by RawTransactionInput constructor taking the TransactionInput as param.
    // As we used segwit=false for the bitcoinSerialize method here we still keep it to not risk to break anything,
    // though it very likely should be fine to replace it with the RawTransactionInput constructor call.
    @Deprecated
    private RawTransactionInput getRawInputFromTransactionInput(TransactionInput input) {
        checkNotNull(input, "input must not be null");
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        checkNotNull(input.getConnectedOutput().getParentTransaction(),
                "input.getConnectedOutput().getParentTransaction() must not be null");
        checkNotNull(input.getValue(), "input.getValue() must not be null");

        // bitcoinSerialize(false) is used just in case the serialized tx is parsed by a bisq node still using
        // bitcoinj 0.14. This is not supposed to happen ever since Version.TRADE_PROTOCOL_VERSION was set to 3,
        // but it costs nothing to be on the safe side.
        // The serialized tx is just used to obtain its hash, so the witness data is not relevant.
        return new RawTransactionInput(input.getOutpoint().getIndex(),
                input.getConnectedOutput().getParentTransaction().bitcoinSerialize(false),
                input.getValue().value);
    }

    private TransactionInput getTransactionInput(Transaction parentTransaction,
                                                 byte[] scriptProgram,
                                                 RawTransactionInput rawTransactionInput) {
        return new TransactionInput(params,
                parentTransaction,
                scriptProgram,
                getConnectedOutPoint(rawTransactionInput),
                Coin.valueOf(rawTransactionInput.value));
    }

    private TransactionOutPoint getConnectedOutPoint(RawTransactionInput rawTransactionInput) {
        return new TransactionOutPoint(params, rawTransactionInput.index,
                new Transaction(params, rawTransactionInput.parentTransaction));
    }

    public boolean isP2WH(RawTransactionInput rawTransactionInput) {
        return ScriptPattern.isP2WH(
                checkNotNull(getConnectedOutPoint(rawTransactionInput).getConnectedOutput()).getScriptPubKey());
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

    static Script get2of2MultiSigRedeemScript(byte[] buyerPubKey, byte[] sellerPubKey) {
        ECKey buyerKey = ECKey.fromPublicOnly(buyerPubKey);
        ECKey sellerKey = ECKey.fromPublicOnly(sellerPubKey);
        // Take care of sorting! Need to reverse to the order we use normally (buyer, seller)
        List<ECKey> keys = ImmutableList.of(sellerKey, buyerKey);
        return ScriptBuilder.createMultiSigOutputScript(2, keys);
    }

    static Script get2of2MultiSigOutputScript(byte[] buyerPubKey, byte[] sellerPubKey, boolean legacy) {
        Script redeemScript = get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        if (legacy) {
            return ScriptBuilder.createP2SHOutputScript(redeemScript);
        } else {
            return ScriptBuilder.createP2WSHOutputScript(redeemScript);
        }
    }

    private Transaction createPayoutTx(Transaction depositTx,
                                       Coin buyerPayoutAmount,
                                       Coin sellerPayoutAmount,
                                       String buyerAddressString,
                                       String sellerAddressString) throws AddressFormatException {
        TransactionOutput hashedMultiSigOutput = depositTx.getOutput(0);
        Transaction transaction = new Transaction(params);
        transaction.addInput(hashedMultiSigOutput);
        if (buyerPayoutAmount.isPositive()) {
            transaction.addOutput(buyerPayoutAmount, Address.fromString(params, buyerAddressString));
        }
        if (sellerPayoutAmount.isPositive()) {
            transaction.addOutput(sellerPayoutAmount, Address.fromString(params, sellerAddressString));
        }
        checkArgument(transaction.getOutputs().size() >= 1, "We need at least one output.");
        return transaction;
    }

    private void signInput(Transaction transaction, TransactionInput input, int inputIndex) throws SigningException {
        checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
        Script scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        ECKey sigKey = LowRSigningKey.from(input.getOutpoint().getConnectedKey(wallet));
        checkNotNull(sigKey, "signInput: sigKey must not be null. input.getOutpoint()=%s", input.getOutpoint());
        if (sigKey.isEncrypted()) {
            checkNotNull(aesKey);
        }

        if (ScriptPattern.isP2PK(scriptPubKey) || ScriptPattern.isP2PKH(scriptPubKey)) {
            Sha256Hash hash = transaction.hashForSignature(inputIndex, scriptPubKey, Transaction.SigHash.ALL, false);
            ECKey.ECDSASignature signature = sigKey.sign(hash, aesKey);
            TransactionSignature txSig = new TransactionSignature(signature, Transaction.SigHash.ALL, false);
            if (ScriptPattern.isP2PK(scriptPubKey)) {
                input.setScriptSig(ScriptBuilder.createInputScript(txSig));
            } else if (ScriptPattern.isP2PKH(scriptPubKey)) {
                input.setScriptSig(ScriptBuilder.createInputScript(txSig, sigKey));
            }
        } else if (ScriptPattern.isP2WPKH(scriptPubKey)) {
            // scriptCode is expected to have the format of a legacy P2PKH output script
            Script scriptCode = ScriptBuilder.createP2PKHOutputScript(sigKey);
            Coin value = input.getValue();
            TransactionSignature txSig = transaction.calculateWitnessSignature(inputIndex, sigKey, aesKey, scriptCode, value,
                    Transaction.SigHash.ALL, false);
            input.setScriptSig(ScriptBuilder.createEmpty());
            input.setWitness(TransactionWitness.redeemP2WPKH(txSig, sigKey));
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

    static void applyLockTime(long lockTime, Transaction tx) {
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
