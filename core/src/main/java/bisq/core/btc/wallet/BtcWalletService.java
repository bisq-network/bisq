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

import bisq.core.btc.exceptions.AddressEntryException;
import bisq.core.btc.exceptions.InsufficientFundsException;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.AddressEntryList;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;

import bisq.common.handlers.ErrorMessageHandler;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import javax.inject.Inject;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import org.spongycastle.crypto.params.KeyParameter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class BtcWalletService extends WalletService {
    private static final Logger log = LoggerFactory.getLogger(BtcWalletService.class);

    private final AddressEntryList addressEntryList;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BtcWalletService(WalletsSetup walletsSetup,
                            AddressEntryList addressEntryList,
                            Preferences preferences,
                            FeeService feeService) {
        super(walletsSetup,
                preferences,
                feeService);

        this.addressEntryList = addressEntryList;

        walletsSetup.addSetupCompletedHandler(() -> {
            wallet = walletsSetup.getBtcWallet();
            wallet.addEventListener(walletEventListener);

            walletsSetup.getChain().addNewBestBlockListener(block -> chainHeightProperty.set(block.getHeight()));
            chainHeightProperty.set(walletsSetup.getChain().getBestChainHeight());
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Overridden Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    void decryptWallet(@NotNull KeyParameter key) {
        super.decryptWallet(key);

        addressEntryList.stream().forEach(e -> {
            final DeterministicKey keyPair = e.getKeyPair();
            if (keyPair.isEncrypted())
                e.setDeterministicKey(keyPair.decrypt(key));
        });
        addressEntryList.persist();
    }

    @Override
    void encryptWallet(KeyCrypterScrypt keyCrypterScrypt, KeyParameter key) {
        super.encryptWallet(keyCrypterScrypt, key);
        addressEntryList.stream().forEach(e -> {
            final DeterministicKey keyPair = e.getKeyPair();
            if (keyPair.isEncrypted())
                e.setDeterministicKey(keyPair.encrypt(keyCrypterScrypt, key));
        });
        addressEntryList.persist();
    }

    @Override
    String getWalletAsString(boolean includePrivKeys) {
        StringBuilder sb = new StringBuilder();
        getAddressEntryListAsImmutableList().stream().forEach(e -> sb.append(e.toString()).append("\n"));
        return "Address entry list:\n" +
                sb.toString() +
                "\n\n" +
                wallet.toString(includePrivKeys, true, true, walletsSetup.getChain()) + "\n\n" +
                "All pubKeys as hex:\n" +
                wallet.printAllPubKeysAsHex();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Burn BSQ txs (some proposal txs, asset listing fee tx, proof of burn tx)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction completePreparedBurnBsqTx(Transaction preparedBurnFeeTx, byte[] opReturnData)
            throws WalletException, InsufficientMoneyException, TransactionVerificationException {
        return completePreparedProposalTx(preparedBurnFeeTx, opReturnData, null, null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Proposal txs
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction completePreparedReimbursementRequestTx(Coin issuanceAmount, Address issuanceAddress, Transaction feeTx, byte[] opReturnData)
            throws TransactionVerificationException, WalletException, InsufficientMoneyException {
        return completePreparedProposalTx(feeTx, opReturnData, issuanceAmount, issuanceAddress);
    }

    public Transaction completePreparedCompensationRequestTx(Coin issuanceAmount, Address issuanceAddress, Transaction feeTx, byte[] opReturnData)
            throws TransactionVerificationException, WalletException, InsufficientMoneyException {
        return completePreparedProposalTx(feeTx, opReturnData, issuanceAmount, issuanceAddress);
    }

    private Transaction completePreparedProposalTx(Transaction feeTx, byte[] opReturnData,
                                                   @Nullable Coin issuanceAmount, @Nullable Address issuanceAddress)
            throws TransactionVerificationException, WalletException, InsufficientMoneyException {

        // (BsqFee)tx has following structure:
        // inputs [1-n] BSQ inputs (fee)
        // outputs [0-1] BSQ request fee change output (>= 546 Satoshi)

        // preparedCompensationRequestTx has following structure:
        // inputs [1-n] BSQ inputs for request fee
        // inputs [1-n] BTC inputs for BSQ issuance and miner fee
        // outputs [1] Mandatory BSQ request fee change output (>= 546 Satoshi)
        // outputs [1] Potentially BSQ issuance output (>= 546 Satoshi) - in case of a issuance tx, otherwise that output does not exist
        // outputs [0-1] BTC change output from issuance and miner fee inputs (>= 546 Satoshi)
        // outputs [1] OP_RETURN with opReturnData and amount 0
        // mining fee: BTC mining fee + burned BSQ fee

        Transaction preparedTx = new Transaction(params);
        // Copy inputs from BSQ fee tx
        feeTx.getInputs().forEach(preparedTx::addInput);
        int indexOfBtcFirstInput = feeTx.getInputs().size();

        // Need to be first because issuance is not guaranteed to be valid and would otherwise burn change output!
        // BSQ change outputs from BSQ fee inputs.
        feeTx.getOutputs().forEach(preparedTx::addOutput);

        // For generic proposals there is no issuance output, for compensation and reimburse requests there is
        if (issuanceAmount != null && issuanceAddress != null) {
            // BSQ issuance output
            preparedTx.addOutput(issuanceAmount, issuanceAddress);
        }

        // safety check counter to avoid endless loops
        int counter = 0;
        // estimated size of input sig
        int sigSizePerInput = 106;
        // typical size for a tx with 3 inputs
        int txSizeWithUnsignedInputs = 300;
        Coin txFeePerByte = feeService.getTxFeePerByte();

        Address changeAddress = getFreshAddressEntry().getAddress();
        checkNotNull(changeAddress, "changeAddress must not be null");

        BtcCoinSelector coinSelector = new BtcCoinSelector(walletsSetup.getAddressesByContext(AddressEntry.Context.AVAILABLE),
                preferences.getIgnoreDustThreshold());
        List<TransactionInput> preparedBsqTxInputs = preparedTx.getInputs();
        List<TransactionOutput> preparedBsqTxOutputs = preparedTx.getOutputs();
        int numInputs = preparedBsqTxInputs.size();
        Transaction resultTx = null;
        boolean isFeeOutsideTolerance;
        do {
            counter++;
            if (counter >= 10) {
                checkNotNull(resultTx, "resultTx must not be null");
                log.error("Could not calculate the fee. Tx=" + resultTx);
                break;
            }

            Transaction tx = new Transaction(params);
            preparedBsqTxInputs.forEach(tx::addInput);
            preparedBsqTxOutputs.forEach(tx::addOutput);

            SendRequest sendRequest = SendRequest.forTx(tx);
            sendRequest.shuffleOutputs = false;
            sendRequest.aesKey = aesKey;
            // signInputs needs to be false as it would try to sign all inputs (BSQ inputs are not in this wallet)
            sendRequest.signInputs = false;

            sendRequest.fee = txFeePerByte.multiply(txSizeWithUnsignedInputs + sigSizePerInput * numInputs);
            sendRequest.feePerKb = Coin.ZERO;
            sendRequest.ensureMinRequiredFee = false;

            sendRequest.coinSelector = coinSelector;
            sendRequest.changeAddress = changeAddress;
            wallet.completeTx(sendRequest);

            resultTx = sendRequest.tx;

            // add OP_RETURN output
            resultTx.addOutput(new TransactionOutput(params, resultTx, Coin.ZERO, ScriptBuilder.createOpReturnScript(opReturnData).getProgram()));

            numInputs = resultTx.getInputs().size();
            txSizeWithUnsignedInputs = resultTx.bitcoinSerialize().length;
            long estimatedFeeAsLong = txFeePerByte.multiply(txSizeWithUnsignedInputs + sigSizePerInput * numInputs).value;
            // calculated fee must be inside of a tolerance range with tx fee
            isFeeOutsideTolerance = Math.abs(resultTx.getFee().value - estimatedFeeAsLong) > 1000;
        }
        while (isFeeOutsideTolerance);

        // Sign all BTC inputs
        signAllBtcInputs(indexOfBtcFirstInput, resultTx);

        checkWalletConsistency(wallet);
        verifyTransaction(resultTx);

        // printTx("BTC wallet: Signed tx", resultTx);
        return resultTx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Blind vote tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We add BTC inputs to pay miner fees and sign the BTC tx inputs

    // (BsqFee)tx has following structure:
    // inputs [1-n] BSQ inputs (fee + stake)
    // outputs [1] BSQ stake
    // outputs [0-1] BSQ change output (>= 546 Satoshi)

    // preparedVoteTx has following structure:
    // inputs [1-n] BSQ inputs (fee + stake)
    // inputs [1-n] BTC inputs for miner fee
    // outputs [1] BSQ stake
    // outputs [0-1] BSQ change output (>= 546 Satoshi)
    // outputs [0-1] BTC change output from miner fee inputs (>= 546 Satoshi)
    // outputs [1] OP_RETURN with opReturnData and amount 0
    // mining fee: BTC mining fee + burned BSQ fee
    public Transaction completePreparedBlindVoteTx(Transaction preparedTx, byte[] opReturnData)
            throws TransactionVerificationException, WalletException, InsufficientMoneyException {
        // First input index for btc inputs (they get added after bsq inputs)
        return completePreparedBsqTxWithBtcFee(preparedTx, opReturnData);
    }

    private Transaction completePreparedBsqTxWithBtcFee(Transaction preparedTx, byte[] opReturnData) throws InsufficientMoneyException, TransactionVerificationException, WalletException {
        // Remember index for first BTC input
        int indexOfBtcFirstInput = preparedTx.getInputs().size();

        Transaction tx = addInputsForMinerFee(preparedTx, opReturnData);
        signAllBtcInputs(indexOfBtcFirstInput, tx);

        checkWalletConsistency(wallet);
        verifyTransaction(tx);

        // printTx("BTC wallet: Signed tx", tx);
        return tx;
    }

    private Transaction addInputsForMinerFee(Transaction preparedTx, byte[] opReturnData) throws InsufficientMoneyException {
        // safety check counter to avoid endless loops
        int counter = 0;
        // estimated size of input sig
        int sigSizePerInput = 106;
        // typical size for a tx with 3 inputs
        int txSizeWithUnsignedInputs = 300;
        Coin txFeePerByte = feeService.getTxFeePerByte();

        Address changeAddress = getFreshAddressEntry().getAddress();
        checkNotNull(changeAddress, "changeAddress must not be null");

        BtcCoinSelector coinSelector = new BtcCoinSelector(walletsSetup.getAddressesByContext(AddressEntry.Context.AVAILABLE),
                preferences.getIgnoreDustThreshold());
        List<TransactionInput> preparedBsqTxInputs = preparedTx.getInputs();
        List<TransactionOutput> preparedBsqTxOutputs = preparedTx.getOutputs();
        int numInputs = preparedBsqTxInputs.size();
        Transaction resultTx = null;
        boolean isFeeOutsideTolerance;
        do {
            counter++;
            if (counter >= 10) {
                checkNotNull(resultTx, "resultTx must not be null");
                log.error("Could not calculate the fee. Tx=" + resultTx);
                break;
            }

            Transaction tx = new Transaction(params);
            preparedBsqTxInputs.forEach(tx::addInput);
            preparedBsqTxOutputs.forEach(tx::addOutput);

            SendRequest sendRequest = SendRequest.forTx(tx);
            sendRequest.shuffleOutputs = false;
            sendRequest.aesKey = aesKey;
            // signInputs needs to be false as it would try to sign all inputs (BSQ inputs are not in this wallet)
            sendRequest.signInputs = false;

            sendRequest.fee = txFeePerByte.multiply(txSizeWithUnsignedInputs + sigSizePerInput * numInputs);
            sendRequest.feePerKb = Coin.ZERO;
            sendRequest.ensureMinRequiredFee = false;

            sendRequest.coinSelector = coinSelector;
            sendRequest.changeAddress = changeAddress;
            wallet.completeTx(sendRequest);

            resultTx = sendRequest.tx;

            // add OP_RETURN output
            resultTx.addOutput(new TransactionOutput(params, resultTx, Coin.ZERO, ScriptBuilder.createOpReturnScript(opReturnData).getProgram()));

            numInputs = resultTx.getInputs().size();
            txSizeWithUnsignedInputs = resultTx.bitcoinSerialize().length;
            final long estimatedFeeAsLong = txFeePerByte.multiply(txSizeWithUnsignedInputs + sigSizePerInput * numInputs).value;
            // calculated fee must be inside of a tolerance range with tx fee
            isFeeOutsideTolerance = Math.abs(resultTx.getFee().value - estimatedFeeAsLong) > 1000;
        }
        while (isFeeOutsideTolerance);
        return resultTx;
    }

    private void signAllBtcInputs(int indexOfBtcFirstInput, Transaction tx) throws TransactionVerificationException {
        for (int i = indexOfBtcFirstInput; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInputs().get(i);
            checkArgument(input.getConnectedOutput() != null && input.getConnectedOutput().isMine(wallet),
                    "input.getConnectedOutput() is not in our wallet. That must not happen.");
            signTransactionInput(wallet, aesKey, tx, input, i);
            checkScriptSig(tx, input, i);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Vote reveal tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We add BTC fees to the prepared reveal tx
    // (BsqFee)tx has following structure:
    // inputs [1] BSQ input (stake)
    // output [1] BSQ unlocked stake

    // preparedVoteTx has following structure:
    // inputs [1] BSQ inputs (stake)
    // inputs [1-n] BTC inputs for miner fee
    // outputs [1] BSQ unlocked stake
    // outputs [0-1] BTC change output from miner fee inputs (>= 546 Satoshi)
    // outputs [1] OP_RETURN with opReturnData and amount 0
    // mining fee: BTC mining fee + burned BSQ fee
    public Transaction completePreparedVoteRevealTx(Transaction preparedTx, byte[] opReturnData)
            throws TransactionVerificationException, WalletException, InsufficientMoneyException {
        return completePreparedBsqTxWithBtcFee(preparedTx, opReturnData);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Add fee input to prepared BSQ send tx
    ///////////////////////////////////////////////////////////////////////////////////////////


    public Transaction completePreparedSendBsqTx(Transaction preparedBsqTx, boolean isSendTx) throws
            TransactionVerificationException, WalletException, InsufficientMoneyException {
        // preparedBsqTx has following structure:
        // inputs [1-n] BSQ inputs
        // outputs [0-1] BSQ receivers output
        // outputs [0-1] BSQ change output

        // We add BTC mining fee. Result tx looks like:
        // inputs [1-n] BSQ inputs
        // inputs [1-n] BTC inputs
        // outputs [0-1] BSQ receivers output
        // outputs [0-1] BSQ change output
        // outputs [0-1] BTC change output
        // mining fee: BTC mining fee
        return completePreparedBsqTx(preparedBsqTx, isSendTx, null);
    }

    public Transaction completePreparedBsqTx(Transaction preparedBsqTx, boolean useCustomTxFee, @Nullable byte[] opReturnData) throws
            TransactionVerificationException, WalletException, InsufficientMoneyException {

        // preparedBsqTx has following structure:
        // inputs [1-n] BSQ inputs
        // outputs [0-1] BSQ receivers output
        // outputs [0-1] BSQ change output
        // mining fee: optional burned BSQ fee (only if opReturnData != null)

        // We add BTC mining fee. Result tx looks like:
        // inputs [1-n] BSQ inputs
        // inputs [1-n] BTC inputs
        // outputs [0-1] BSQ receivers output
        // outputs [0-1] BSQ change output
        // outputs [0-1] BTC change output
        // outputs [0-1] OP_RETURN with opReturnData (only if opReturnData != null)
        // mining fee: BTC mining fee + optional burned BSQ fee (only if opReturnData != null)

        // In case of txs for burned BSQ fees we have no receiver output and it might be that there is no change outputs
        // We need to guarantee that min. 1 valid output is added (OP_RETURN does not count). So we use a higher input
        // for BTC to force an additional change output.

        // safety check counter to avoid endless loops
        int counter = 0;
        // estimated size of input sig
        int sigSizePerInput = 106;
        // typical size for a tx with 2 inputs
        int txSizeWithUnsignedInputs = 203;
        // If useCustomTxFee we allow overriding the estimated fee from preferences
        Coin txFeePerByte = useCustomTxFee ? getTxFeeForWithdrawalPerByte() : feeService.getTxFeePerByte();
        // In case there are no change outputs we force a change by adding min dust to the BTC input
        Coin forcedChangeValue = Coin.ZERO;

        Address changeAddress = getFreshAddressEntry().getAddress();
        checkNotNull(changeAddress, "changeAddress must not be null");

        BtcCoinSelector coinSelector = new BtcCoinSelector(walletsSetup.getAddressesByContext(AddressEntry.Context.AVAILABLE),
                preferences.getIgnoreDustThreshold());
        List<TransactionInput> preparedBsqTxInputs = preparedBsqTx.getInputs();
        List<TransactionOutput> preparedBsqTxOutputs = preparedBsqTx.getOutputs();
        int numInputs = preparedBsqTxInputs.size() + 1; // We add 1 for the BTC fee input
        Transaction resultTx = null;
        boolean isFeeOutsideTolerance;
        boolean opReturnIsOnlyOutput;
        do {
            counter++;
            if (counter >= 10) {
                checkNotNull(resultTx, "resultTx must not be null");
                log.error("Could not calculate the fee. Tx=" + resultTx);
                break;
            }

            Transaction tx = new Transaction(params);
            preparedBsqTxInputs.stream().forEach(tx::addInput);

            if (forcedChangeValue.isZero()) {
                preparedBsqTxOutputs.stream().forEach(tx::addOutput);
            } else {
                //TODO test that case
                checkArgument(preparedBsqTxOutputs.size() == 0, "preparedBsqTxOutputs.size must be null in that code branch");
                tx.addOutput(forcedChangeValue, changeAddress);
            }

            SendRequest sendRequest = SendRequest.forTx(tx);
            sendRequest.shuffleOutputs = false;
            sendRequest.aesKey = aesKey;
            // signInputs needs to be false as it would try to sign all inputs (BSQ inputs are not in this wallet)
            sendRequest.signInputs = false;

            sendRequest.fee = txFeePerByte.multiply(txSizeWithUnsignedInputs + sigSizePerInput * numInputs);
            sendRequest.feePerKb = Coin.ZERO;
            sendRequest.ensureMinRequiredFee = false;

            sendRequest.coinSelector = coinSelector;
            sendRequest.changeAddress = changeAddress;
            wallet.completeTx(sendRequest);

            resultTx = sendRequest.tx;

            // We might have the rare case that both inputs matched the required fees, so both did not require
            // a change output.
            // In such cases we need to add artificially a change output (OP_RETURN is not allowed as only output)
            opReturnIsOnlyOutput = resultTx.getOutputs().size() == 0;
            forcedChangeValue = opReturnIsOnlyOutput ? Restrictions.getMinNonDustOutput() : Coin.ZERO;

            // add OP_RETURN output
            if (opReturnData != null)
                resultTx.addOutput(new TransactionOutput(params, resultTx, Coin.ZERO, ScriptBuilder.createOpReturnScript(opReturnData).getProgram()));

            numInputs = resultTx.getInputs().size();
            txSizeWithUnsignedInputs = resultTx.bitcoinSerialize().length;
            final long estimatedFeeAsLong = txFeePerByte.multiply(txSizeWithUnsignedInputs + sigSizePerInput * numInputs).value;
            // calculated fee must be inside of a tolerance range with tx fee
            isFeeOutsideTolerance = Math.abs(resultTx.getFee().value - estimatedFeeAsLong) > 1000;
        }
        while (opReturnIsOnlyOutput ||
                isFeeOutsideTolerance ||
                resultTx.getFee().value < txFeePerByte.multiply(resultTx.bitcoinSerialize().length).value);

        // Sign all BTC inputs
        signAllBtcInputs(preparedBsqTxInputs.size(), resultTx);

        checkWalletConsistency(wallet);
        verifyTransaction(resultTx);

        printTx("BTC wallet: Signed tx", resultTx);
        return resultTx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Commit tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void commitTx(Transaction tx) {
        wallet.commitTx(tx);
        // printTx("BTC commit Tx", tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AddressEntry
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<AddressEntry> getAddressEntry(String offerId, @SuppressWarnings("SameParameterValue") AddressEntry.Context context) {
        return getAddressEntryListAsImmutableList().stream()
                .filter(e -> offerId.equals(e.getOfferId()))
                .filter(e -> context == e.getContext())
                .findAny();
    }

    public AddressEntry getOrCreateAddressEntry(String offerId, AddressEntry.Context context) {
        Optional<AddressEntry> addressEntry = getAddressEntryListAsImmutableList().stream()
                .filter(e -> offerId.equals(e.getOfferId()))
                .filter(e -> context == e.getContext())
                .findAny();
        if (addressEntry.isPresent()) {
            return addressEntry.get();
        } else {
            // We try to use available and not yet used entries
            Optional<AddressEntry> emptyAvailableAddressEntry = getAddressEntryListAsImmutableList().stream()
                    .filter(e -> AddressEntry.Context.AVAILABLE == e.getContext())
                    .filter(e -> isAddressUnused(e.getAddress()))
                    .findAny();
            if (emptyAvailableAddressEntry.isPresent()) {
                return addressEntryList.swapAvailableToAddressEntryWithOfferId(emptyAvailableAddressEntry.get(), context, offerId);
            } else {
                AddressEntry entry = new AddressEntry(wallet.freshReceiveKey(), context, offerId);
                addressEntryList.addAddressEntry(entry);
                return entry;
            }
        }
    }

    public AddressEntry getArbitratorAddressEntry() {
        AddressEntry.Context context = AddressEntry.Context.ARBITRATOR;
        Optional<AddressEntry> addressEntry = getAddressEntryListAsImmutableList().stream()
                .filter(e -> context == e.getContext())
                .findAny();
        return getOrCreateAddressEntry(context, addressEntry);
    }

    public AddressEntry getFreshAddressEntry() {
        AddressEntry.Context context = AddressEntry.Context.AVAILABLE;
        Optional<AddressEntry> addressEntry = getAddressEntryListAsImmutableList().stream()
                .filter(e -> context == e.getContext())
                .filter(e -> isAddressUnused(e.getAddress()))
                .findAny();
        return getOrCreateAddressEntry(context, addressEntry);
    }

    public AddressEntry getNewAddressEntry(String offerId, AddressEntry.Context context) {
        AddressEntry entry = new AddressEntry(wallet.freshReceiveKey(), context, offerId);
        addressEntryList.addAddressEntry(entry);
        return entry;
    }

    private AddressEntry getOrCreateAddressEntry(AddressEntry.Context context, Optional<AddressEntry> addressEntry) {
        if (addressEntry.isPresent()) {
            return addressEntry.get();
        } else {
            AddressEntry entry = new AddressEntry(wallet.freshReceiveKey(), context);
            addressEntryList.addAddressEntry(entry);
            return entry;
        }
    }

    private Optional<AddressEntry> findAddressEntry(String address, AddressEntry.Context context) {
        return getAddressEntryListAsImmutableList().stream()
                .filter(e -> address.equals(e.getAddressString()))
                .filter(e -> context == e.getContext())
                .findAny();
    }

    public List<AddressEntry> getAvailableAddressEntries() {
        return getAddressEntryListAsImmutableList().stream()
                .filter(addressEntry -> AddressEntry.Context.AVAILABLE == addressEntry.getContext())
                .collect(Collectors.toList());
    }

    public List<AddressEntry> getAddressEntriesForOpenOffer() {
        return getAddressEntryListAsImmutableList().stream()
                .filter(addressEntry -> AddressEntry.Context.OFFER_FUNDING == addressEntry.getContext() ||
                        AddressEntry.Context.RESERVED_FOR_TRADE == addressEntry.getContext())
                .collect(Collectors.toList());
    }

    public List<AddressEntry> getAddressEntriesForTrade() {
        return getAddressEntryListAsImmutableList().stream()
                .filter(addressEntry -> AddressEntry.Context.MULTI_SIG == addressEntry.getContext() ||
                        AddressEntry.Context.TRADE_PAYOUT == addressEntry.getContext())
                .collect(Collectors.toList());
    }

    public List<AddressEntry> getAddressEntries(AddressEntry.Context context) {
        return getAddressEntryListAsImmutableList().stream()
                .filter(addressEntry -> context == addressEntry.getContext())
                .collect(Collectors.toList());
    }

    public List<AddressEntry> getFundedAvailableAddressEntries() {
        return getAvailableAddressEntries().stream()
                .filter(addressEntry -> getBalanceForAddress(addressEntry.getAddress()).isPositive())
                .collect(Collectors.toList());
    }

    public List<AddressEntry> getAddressEntryListAsImmutableList() {
        return ImmutableList.copyOf(addressEntryList.getList());
    }

    public void swapTradeEntryToAvailableEntry(String offerId, AddressEntry.Context context) {
        Optional<AddressEntry> addressEntryOptional = getAddressEntryListAsImmutableList().stream()
                .filter(e -> offerId.equals(e.getOfferId()))
                .filter(e -> context == e.getContext())
                .findAny();
        addressEntryOptional.ifPresent(e -> {
            log.info("swap addressEntry with address {} and offerId {} from context {} to available",
                    e.getAddressString(), e.getOfferId(), context);
            addressEntryList.swapToAvailable(e);
            saveAddressEntryList();
        });
    }

    public void resetAddressEntriesForOpenOffer(String offerId) {
        log.info("resetAddressEntriesForOpenOffer offerId={}", offerId);
        swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.OFFER_FUNDING);
        swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.RESERVED_FOR_TRADE);
    }

    public void resetAddressEntriesForPendingTrade(String offerId) {
        swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.MULTI_SIG);
        // Don't swap TRADE_PAYOUT as it might be still open in the last trade step to be used for external transfer
    }

    public void swapAnyTradeEntryContextToAvailableEntry(String offerId) {
        resetAddressEntriesForOpenOffer(offerId);
        resetAddressEntriesForPendingTrade(offerId);
    }

    public void saveAddressEntryList() {
        addressEntryList.persist();
    }

    public DeterministicKey getMultiSigKeyPair(String tradeId, byte[] pubKey) {
        Optional<AddressEntry> multiSigAddressEntryOptional = getAddressEntry(tradeId, AddressEntry.Context.MULTI_SIG);
        DeterministicKey multiSigKeyPair;
        if (multiSigAddressEntryOptional.isPresent()) {
            AddressEntry multiSigAddressEntry = multiSigAddressEntryOptional.get();
            multiSigKeyPair = multiSigAddressEntry.getKeyPair();
            if (!Arrays.equals(pubKey, multiSigAddressEntry.getPubKey())) {
                log.error("Pub Key from AddressEntry does not match key pair from trade data. Trade ID={}\n" +
                        "We try to find the keypair in the wallet with the pubKey we found in the trade data.", tradeId);
                multiSigKeyPair = findKeyFromPubKey(pubKey);
            }
        } else {
            log.error("multiSigAddressEntry not found for trade ID={}.\n" +
                    "We try to find the keypair in the wallet with the pubKey we found in the trade data.", tradeId);
            multiSigKeyPair = findKeyFromPubKey(pubKey);
        }

        return multiSigKeyPair;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Balance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getSavingWalletBalance() {
        return Coin.valueOf(getFundedAvailableAddressEntries().stream()
                .mapToLong(addressEntry -> getBalanceForAddress(addressEntry.getAddress()).value)
                .sum());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Double spend unconfirmed transaction (unlock in case we got into a tx with a too low mining fee)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void doubleSpendTransaction(String txId, Runnable resultHandler, ErrorMessageHandler errorMessageHandler)
            throws InsufficientFundsException {
        AddressEntry addressEntry = getFreshAddressEntry();
        checkNotNull(addressEntry.getAddress(), "addressEntry.getAddress() must not be null");
        Optional<Transaction> transactionOptional = wallet.getTransactions(true).stream()
                .filter(t -> t.getHashAsString().equals(txId))
                .findAny();
        if (transactionOptional.isPresent()) {
            Transaction txToDoubleSpend = transactionOptional.get();
            Address toAddress = addressEntry.getAddress();
            final TransactionConfidence.ConfidenceType confidenceType = txToDoubleSpend.getConfidence().getConfidenceType();
            if (confidenceType == TransactionConfidence.ConfidenceType.PENDING) {
                log.debug("txToDoubleSpend no. of inputs " + txToDoubleSpend.getInputs().size());

                Transaction newTransaction = new Transaction(params);
                txToDoubleSpend.getInputs().stream().forEach(input -> {
                            final TransactionOutput connectedOutput = input.getConnectedOutput();
                            if (connectedOutput != null &&
                                    connectedOutput.isMine(wallet) &&
                                    connectedOutput.getParentTransaction() != null &&
                                    connectedOutput.getParentTransaction().getConfidence() != null &&
                                    input.getValue() != null) {
                                //if (connectedOutput.getParentTransaction().getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                                newTransaction.addInput(new TransactionInput(params,
                                        newTransaction,
                                        new byte[]{},
                                        new TransactionOutPoint(params, input.getOutpoint().getIndex(),
                                                new Transaction(params, connectedOutput.getParentTransaction().bitcoinSerialize())),
                                        Coin.valueOf(input.getValue().value)));
                               /* } else {
                                    log.warn("Confidence of parent tx is not of type BUILDING: ConfidenceType=" +
                                            connectedOutput.getParentTransaction().getConfidence().getConfidenceType());
                                }*/
                            }
                        }
                );

                log.info("newTransaction no. of inputs " + newTransaction.getInputs().size());
                log.info("newTransaction size in kB " + newTransaction.bitcoinSerialize().length / 1024);

                if (!newTransaction.getInputs().isEmpty()) {
                    Coin amount = Coin.valueOf(newTransaction.getInputs().stream()
                            .mapToLong(input -> input.getValue() != null ? input.getValue().value : 0)
                            .sum());
                    newTransaction.addOutput(amount, toAddress);

                    try {
                        Coin fee;
                        int counter = 0;
                        int txSize = 0;
                        Transaction tx;
                        SendRequest sendRequest;
                        Coin txFeeForWithdrawalPerByte = getTxFeeForWithdrawalPerByte();
                        do {
                            counter++;
                            fee = txFeeForWithdrawalPerByte.multiply(txSize);
                            newTransaction.clearOutputs();
                            newTransaction.addOutput(amount.subtract(fee), toAddress);

                            sendRequest = SendRequest.forTx(newTransaction);
                            sendRequest.fee = fee;
                            sendRequest.feePerKb = Coin.ZERO;
                            sendRequest.ensureMinRequiredFee = false;
                            sendRequest.aesKey = aesKey;
                            sendRequest.coinSelector = new BtcCoinSelector(toAddress, preferences.getIgnoreDustThreshold());
                            sendRequest.changeAddress = toAddress;
                            wallet.completeTx(sendRequest);
                            tx = sendRequest.tx;
                            txSize = tx.bitcoinSerialize().length;
                            printTx("FeeEstimationTransaction", tx);
                            sendRequest.tx.getOutputs().forEach(o -> log.debug("Output value " + o.getValue().toFriendlyString()));
                        }
                        while (feeEstimationNotSatisfied(counter, tx));

                        if (counter == 10)
                            log.error("Could not calculate the fee. Tx=" + tx);


                        Wallet.SendResult sendResult = null;
                        try {
                            sendRequest = SendRequest.forTx(newTransaction);
                            sendRequest.fee = fee;
                            sendRequest.feePerKb = Coin.ZERO;
                            sendRequest.ensureMinRequiredFee = false;
                            sendRequest.aesKey = aesKey;
                            sendRequest.coinSelector = new BtcCoinSelector(toAddress, preferences.getIgnoreDustThreshold());
                            sendRequest.changeAddress = toAddress;
                            sendResult = wallet.sendCoins(sendRequest);
                        } catch (InsufficientMoneyException e) {
                            // in some cases getFee did not calculate correctly and we still get an InsufficientMoneyException
                            log.warn("We still have a missing fee " + (e.missing != null ? e.missing.toFriendlyString() : ""));

                            amount = amount.subtract(e.missing);
                            newTransaction.clearOutputs();
                            newTransaction.addOutput(amount, toAddress);

                            sendRequest = SendRequest.forTx(newTransaction);
                            sendRequest.fee = fee;
                            sendRequest.feePerKb = Coin.ZERO;
                            sendRequest.ensureMinRequiredFee = false;
                            sendRequest.aesKey = aesKey;
                            sendRequest.coinSelector = new BtcCoinSelector(toAddress,
                                    preferences.getIgnoreDustThreshold(), false);
                            sendRequest.changeAddress = toAddress;

                            try {
                                sendResult = wallet.sendCoins(sendRequest);
                                printTx("FeeEstimationTransaction", newTransaction);
                            } catch (InsufficientMoneyException e2) {
                                errorMessageHandler.handleErrorMessage("We did not get the correct fee calculated. " + (e2.missing != null ? e2.missing.toFriendlyString() : ""));
                            }
                        }
                        if (sendResult != null) {
                            log.info("Broadcasting double spending transaction. " + sendResult.tx);
                            Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
                                @Override
                                public void onSuccess(Transaction result) {
                                    log.info("Double spending transaction published. " + result);
                                    resultHandler.run();
                                }

                                @Override
                                public void onFailure(@NotNull Throwable t) {
                                    log.error("Broadcasting double spending transaction failed. " + t.getMessage());
                                    errorMessageHandler.handleErrorMessage(t.getMessage());
                                }
                            });
                        }

                    } catch (InsufficientMoneyException e) {
                        throw new InsufficientFundsException("The fees for that transaction exceed the available funds " +
                                "or the resulting output value is below the min. dust value:\n" +
                                "Missing " + (e.missing != null ? e.missing.toFriendlyString() : "null"));
                    }
                } else {
                    String errorMessage = "We could not find inputs we control in the transaction we want to double spend.";
                    log.warn(errorMessage);
                    errorMessageHandler.handleErrorMessage(errorMessage);
                }
            } else if (confidenceType == TransactionConfidence.ConfidenceType.BUILDING) {
                errorMessageHandler.handleErrorMessage("That transaction is already in the blockchain so we cannot double spend it.");
            } else if (confidenceType == TransactionConfidence.ConfidenceType.DEAD) {
                errorMessageHandler.handleErrorMessage("One of the inputs of that transaction has been already double spent.");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal Fee calculation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getFeeEstimationTransaction(String fromAddress,
                                                   String toAddress,
                                                   Coin amount,
                                                   AddressEntry.Context context)
            throws AddressFormatException, AddressEntryException, InsufficientFundsException {

        Optional<AddressEntry> addressEntry = findAddressEntry(fromAddress, context);
        if (!addressEntry.isPresent())
            throw new AddressEntryException("WithdrawFromAddress is not found in our wallet.");

        checkNotNull(addressEntry.get().getAddress(), "addressEntry.get().getAddress() must nto be null");

        try {
            Coin fee;
            int counter = 0;
            int txSize = 0;
            Transaction tx;
            Coin txFeeForWithdrawalPerByte = getTxFeeForWithdrawalPerByte();
            do {
                counter++;
                fee = txFeeForWithdrawalPerByte.multiply(txSize);
                SendRequest sendRequest = getSendRequest(fromAddress, toAddress, amount, fee, aesKey, context);
                wallet.completeTx(sendRequest);
                tx = sendRequest.tx;
                txSize = tx.bitcoinSerialize().length;
                printTx("FeeEstimationTransaction", tx);
            }
            while (feeEstimationNotSatisfied(counter, tx));
            if (counter == 10)
                log.error("Could not calculate the fee. Tx=" + tx);

            return tx;
        } catch (InsufficientMoneyException e) {
            throw new InsufficientFundsException("The fees for that transaction exceed the available funds " +
                    "or the resulting output value is below the min. dust value:\n" +
                    "Missing " + (e.missing != null ? e.missing.toFriendlyString() : "null"));
        }
    }

    public Transaction getFeeEstimationTransactionForMultipleAddresses(Set<String> fromAddresses,
                                                                       Coin amount)
            throws AddressFormatException, AddressEntryException, InsufficientFundsException {
        Set<AddressEntry> addressEntries = fromAddresses.stream()
                .map(address -> {
                    Optional<AddressEntry> addressEntryOptional = findAddressEntry(address, AddressEntry.Context.AVAILABLE);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.OFFER_FUNDING);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.TRADE_PAYOUT);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.ARBITRATOR);
                    return addressEntryOptional;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        if (addressEntries.isEmpty())
            throw new AddressEntryException("No Addresses for withdraw  found in our wallet");

        try {
            Coin fee;
            int counter = 0;
            int txSize = 0;
            Transaction tx;
            Coin txFeeForWithdrawalPerByte = getTxFeeForWithdrawalPerByte();
            do {
                counter++;
                fee = txFeeForWithdrawalPerByte.multiply(txSize);
                // We use a dummy address for the output
                final String dummyReceiver = getFreshAddressEntry().getAddressString();
                SendRequest sendRequest = getSendRequestForMultipleAddresses(fromAddresses, dummyReceiver, amount, fee, null, aesKey);
                wallet.completeTx(sendRequest);
                tx = sendRequest.tx;
                txSize = tx.bitcoinSerialize().length;
                printTx("FeeEstimationTransactionForMultipleAddresses", tx);
            }
            while (feeEstimationNotSatisfied(counter, tx));
            if (counter == 10)
                log.error("Could not calculate the fee. Tx=" + tx);

            return tx;
        } catch (InsufficientMoneyException e) {
            throw new InsufficientFundsException("The fees for that transaction exceed the available funds " +
                    "or the resulting output value is below the min. dust value:\n" +
                    "Missing " + (e.missing != null ? e.missing.toFriendlyString() : "null"));
        }
    }

    private boolean feeEstimationNotSatisfied(int counter, Transaction tx) {
        long targetFee = getTxFeeForWithdrawalPerByte().multiply(tx.bitcoinSerialize().length).value;
        return counter < 10 &&
                (tx.getFee().value < targetFee ||
                        tx.getFee().value - targetFee > 1000);
    }

    public int getEstimatedFeeTxSize(List<Coin> outputValues, Coin txFee)
            throws InsufficientMoneyException, AddressFormatException {
        Transaction transaction = new Transaction(params);
        Address dummyAddress = wallet.currentReceiveKey().toAddress(params);
        outputValues.forEach(outputValue -> transaction.addOutput(outputValue, dummyAddress));

        SendRequest sendRequest = SendRequest.forTx(transaction);
        sendRequest.shuffleOutputs = false;
        sendRequest.aesKey = aesKey;
        sendRequest.coinSelector = new BtcCoinSelector(walletsSetup.getAddressesByContext(AddressEntry.Context.AVAILABLE),
                preferences.getIgnoreDustThreshold());
        sendRequest.fee = txFee;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.ensureMinRequiredFee = false;
        sendRequest.changeAddress = dummyAddress;
        wallet.completeTx(sendRequest);
        return transaction.bitcoinSerialize().length;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal Send
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String sendFunds(String fromAddress,
                            String toAddress,
                            Coin receiverAmount,
                            Coin fee,
                            @Nullable KeyParameter aesKey,
                            @SuppressWarnings("SameParameterValue") AddressEntry.Context context,
                            FutureCallback<Transaction> callback) throws AddressFormatException,
            AddressEntryException, InsufficientMoneyException {
        SendRequest sendRequest = getSendRequest(fromAddress, toAddress, receiverAmount, fee, aesKey, context);
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printTx("sendFunds", sendResult.tx);
        return sendResult.tx.getHashAsString();
    }

    public String sendFundsForMultipleAddresses(Set<String> fromAddresses,
                                                String toAddress,
                                                Coin receiverAmount,
                                                Coin fee,
                                                @Nullable String changeAddress,
                                                @Nullable KeyParameter aesKey,
                                                FutureCallback<Transaction> callback) throws AddressFormatException,
            AddressEntryException, InsufficientMoneyException {

        SendRequest request = getSendRequestForMultipleAddresses(fromAddresses, toAddress, receiverAmount, fee, changeAddress, aesKey);
        Wallet.SendResult sendResult = wallet.sendCoins(request);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printTx("sendFunds", sendResult.tx);
        return sendResult.tx.getHashAsString();
    }

    private SendRequest getSendRequest(String fromAddress,
                                       String toAddress,
                                       Coin amount,
                                       Coin fee,
                                       @Nullable KeyParameter aesKey,
                                       AddressEntry.Context context) throws AddressFormatException,
            AddressEntryException {
        Transaction tx = new Transaction(params);
        final Coin receiverAmount = amount.subtract(fee);
        Preconditions.checkArgument(Restrictions.isAboveDust(receiverAmount),
                "The amount is too low (dust limit).");
        tx.addOutput(receiverAmount, Address.fromBase58(params, toAddress));

        SendRequest sendRequest = SendRequest.forTx(tx);
        sendRequest.fee = fee;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.ensureMinRequiredFee = false;
        sendRequest.aesKey = aesKey;
        sendRequest.shuffleOutputs = false;
        Optional<AddressEntry> addressEntry = findAddressEntry(fromAddress, context);
        if (!addressEntry.isPresent())
            throw new AddressEntryException("WithdrawFromAddress is not found in our wallet.");

        checkNotNull(addressEntry.get(), "addressEntry.get() must not be null");
        checkNotNull(addressEntry.get().getAddress(), "addressEntry.get().getAddress() must not be null");
        sendRequest.coinSelector = new BtcCoinSelector(addressEntry.get().getAddress(), preferences.getIgnoreDustThreshold());
        sendRequest.changeAddress = addressEntry.get().getAddress();
        return sendRequest;
    }

    private SendRequest getSendRequestForMultipleAddresses(Set<String> fromAddresses,
                                                           String toAddress,
                                                           Coin amount,
                                                           Coin fee,
                                                           @Nullable String changeAddress,
                                                           @Nullable KeyParameter aesKey) throws
            AddressFormatException, AddressEntryException, InsufficientMoneyException {
        Transaction tx = new Transaction(params);
        checkArgument(Restrictions.isAboveDust(amount),
                "The amount is too low (dust limit).");

        final Coin netValue = amount.subtract(fee);
        if (netValue.isNegative())
            throw new InsufficientMoneyException(netValue.multiply(-1), "The mining fee for that transaction exceed the available amount.");

        tx.addOutput(netValue, Address.fromBase58(params, toAddress));

        SendRequest sendRequest = SendRequest.forTx(tx);
        sendRequest.fee = fee;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.ensureMinRequiredFee = false;
        sendRequest.aesKey = aesKey;
        sendRequest.shuffleOutputs = false;
        Set<AddressEntry> addressEntries = fromAddresses.stream()
                .map(address -> {
                    Optional<AddressEntry> addressEntryOptional = findAddressEntry(address, AddressEntry.Context.AVAILABLE);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.OFFER_FUNDING);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.TRADE_PAYOUT);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.ARBITRATOR);
                    return addressEntryOptional;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        if (addressEntries.isEmpty())
            throw new AddressEntryException("No Addresses for withdraw found in our wallet");

        sendRequest.coinSelector = new BtcCoinSelector(walletsSetup.getAddressesFromAddressEntries(addressEntries),
                preferences.getIgnoreDustThreshold());
        Optional<AddressEntry> addressEntryOptional = Optional.<AddressEntry>empty();
        AddressEntry changeAddressAddressEntry = null;
        if (changeAddress != null)
            addressEntryOptional = findAddressEntry(changeAddress, AddressEntry.Context.AVAILABLE);

        changeAddressAddressEntry = addressEntryOptional.orElseGet(() -> getFreshAddressEntry());
        checkNotNull(changeAddressAddressEntry, "change address must not be null");
        sendRequest.changeAddress = changeAddressAddressEntry.getAddress();
        return sendRequest;
    }

    // We ignore utxos which are considered dust attacks for spying on users wallets.
    // The ignoreDustThreshold value is set in the preferences. If not set we use default non dust
    // value of 546 sat.
    @Override
    protected boolean isDustAttackUtxo(TransactionOutput output) {
        return output.getValue().value < preferences.getIgnoreDustThreshold();
    }
}
