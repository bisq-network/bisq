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

package bisq.core.trade.protocol.bisq_v1.tasks;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletUtils;
import bisq.core.trade.validation.TradePeerTxInputValidator;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TradePeerTxInputValidatorTest {
    private static final NetworkParameters PARAMS = RegTestParams.get();
    private static final String MAKER_ROLE = "Maker";
    private static final String TAKER_ROLE = "Taker";

    private BtcWalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = mock(BtcWalletService.class);
        when(walletService.getTxFromSerializedTx(any(byte[].class)))
                .thenAnswer(invocation -> new Transaction(PARAMS, invocation.getArgument(0)));
        when(walletService.isP2WH(any(RawTransactionInput.class)))
                .thenAnswer(invocation -> WalletUtils.isP2WH(invocation.getArgument(0), PARAMS));
    }

    @Test
    void acceptsExactExpectedInputAmountForP2WHInputs() {
        List<RawTransactionInput> rawTransactionInputs = Arrays.asList(
                rawInput(parentTxWithP2WHOutput(40_000)),
                rawInput(parentTxWithP2WHOutput(60_000)));

        assertDoesNotThrow(() -> TradePeerTxInputValidator.validatePeersInputs(
                rawTransactionInputs,
                Coin.valueOf(100_000),
                walletService,
                MAKER_ROLE));
    }

    @Test
    void rejectsInputAmountMismatch() {
        List<RawTransactionInput> rawTransactionInputs = Collections.singletonList(
                rawInput(parentTxWithP2WHOutput(100_000)));

        assertThrows(IllegalArgumentException.class,
                () -> TradePeerTxInputValidator.validatePeersInputs(
                        rawTransactionInputs,
                        Coin.valueOf(99_999),
                        walletService,
                        MAKER_ROLE));
    }

    @Test
    void rejectsNullInputList() {
        assertThrows(NullPointerException.class,
                () -> TradePeerTxInputValidator.validatePeersInputs(null, Coin.valueOf(1), walletService, TAKER_ROLE));
    }

    @Test
    void rejectsEmptyInputList() {
        assertThrows(IllegalArgumentException.class,
                () -> TradePeerTxInputValidator.validatePeersInputs(List.of(), Coin.valueOf(1), walletService, TAKER_ROLE));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(NullPointerException.class,
                () -> TradePeerTxInputValidator.validatePeersInputs(
                        Arrays.asList(rawInput(parentTxWithP2WHOutput(1)), null),
                        Coin.valueOf(1),
                        walletService,
                        TAKER_ROLE));
    }

    @Test
    void rejectsNullExpectedInputAmount() {
        List<RawTransactionInput> rawTransactionInputs = Collections.singletonList(
                rawInput(parentTxWithP2WHOutput(100_000)));

        assertThrows(NullPointerException.class,
                () -> TradePeerTxInputValidator.validatePeersInputs(rawTransactionInputs, null, walletService, MAKER_ROLE));
    }

    @Test
    void rejectsNonPositiveExpectedInputAmount() {
        List<RawTransactionInput> rawTransactionInputs = Collections.singletonList(
                rawInput(parentTxWithP2WHOutput(100_000)));

        assertThrows(IllegalArgumentException.class,
                () -> TradePeerTxInputValidator.validatePeersInputs(rawTransactionInputs, Coin.ZERO, walletService, MAKER_ROLE));
    }

    @Test
    void rejectsInputValueMismatchWithParentTxOutput() {
        Transaction parentTx = parentTxWithP2WHOutput(100_000);
        RawTransactionInput rawTransactionInput = rawInputWithValue(parentTx, 100_001);

        assertThrows(IllegalArgumentException.class,
                () -> TradePeerTxInputValidator.validatePeersInputs(
                        Collections.singletonList(rawTransactionInput),
                        Coin.valueOf(100_001),
                        walletService,
                        TAKER_ROLE));
    }

    @Test
    void rejectsNonP2WHInput() {
        List<RawTransactionInput> rawTransactionInputs = Collections.singletonList(
                rawInput(parentTxWithP2pkhOutput(100_000)));

        assertThrows(IllegalArgumentException.class,
                () -> TradePeerTxInputValidator.validatePeersInputs(
                        rawTransactionInputs,
                        Coin.valueOf(100_000),
                        walletService,
                        TAKER_ROLE));
    }

    @Test
    void rejectsMalformedParentTransaction() {
        RawTransactionInput rawTransactionInput = rawInputWithParentTransaction(new byte[]{1}, 100_000);

        ProtocolException exception = assertThrows(ProtocolException.class,
                () -> TradePeerTxInputValidator.validatePeersInputs(
                        Collections.singletonList(rawTransactionInput),
                        Coin.valueOf(100_000),
                        walletService,
                        MAKER_ROLE));

        assertEquals(ArrayIndexOutOfBoundsException.class, exception.getCause().getClass());
    }

    private static RawTransactionInput rawInput(Transaction parentTx) {
        Transaction spendingTx = new Transaction(PARAMS);
        TransactionInput input = spendingTx.addInput(parentTx.getOutput(0));
        return new RawTransactionInput(input);
    }

    private static RawTransactionInput rawInputWithValue(Transaction parentTx, long value) {
        return rawInputWithParentTransaction(parentTx.bitcoinSerialize(), value);
    }

    private static RawTransactionInput rawInputWithParentTransaction(byte[] parentTransaction, long value) {
        return RawTransactionInput.fromProto(protobuf.RawTransactionInput.newBuilder()
                .setIndex(0)
                .setParentTransaction(ByteString.copyFrom(parentTransaction))
                .setValue(value)
                .build());
    }

    private static Transaction parentTxWithP2WHOutput(long value) {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, ScriptBuilder.createEmpty());
        tx.addOutput(Coin.valueOf(value), SegwitAddress.fromKey(PARAMS, new ECKey()));
        return tx;
    }

    private static Transaction parentTxWithP2pkhOutput(long value) {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, ScriptBuilder.createEmpty());
        Address address = Address.fromKey(PARAMS, new ECKey(), Script.ScriptType.P2PKH);
        tx.addOutput(Coin.valueOf(value), address);
        return tx;
    }
}
