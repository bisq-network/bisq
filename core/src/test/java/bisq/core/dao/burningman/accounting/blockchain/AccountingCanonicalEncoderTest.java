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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.burningman.accounting.blockchain;

import bisq.common.encoding.canonical.CanonicalEncoder;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AccountingCanonicalEncoderTest {
    @Test
    public void accountingTxOutputEncodeCanonicalMatchesProtobuf() {
        protobuf.AccountingTxOutput proto = getAccountingTxOutputProto();
        AccountingTxOutput output = AccountingTxOutput.fromProto(proto);

        assertArrayEquals(output.toProtoMessage().toByteArray(),
                output.encodeCanonical(CanonicalEncoder.DEFAULT));
        assertArrayEquals(output.encodeCanonical(CanonicalEncoder.DEFAULT),
                output.serializeForHash());
    }

    @Test
    public void accountingTxOutputRejectsNegativeValues() {
        AccountingTxOutput output = new AccountingTxOutput(-1, "candidate-name");

        assertThrows(IllegalArgumentException.class, output::toProtoMessage);
    }

    @Test
    public void accountingTxOutputSupportsFullUInt32Range() {
        AccountingTxOutput output = new AccountingTxOutput(0xFFFF_FFFFL, "candidate-name");
        protobuf.AccountingTxOutput proto = output.toProtoMessage();

        assertEquals(-1, proto.getValue());
        assertEquals(0xFFFF_FFFFL, AccountingTxOutput.fromProto(proto).getValue());
        assertArrayEquals(proto.toByteArray(), output.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void accountingTxEncodeCanonicalMatchesProtobuf() {
        protobuf.AccountingTx proto = getAccountingTxProto();
        AccountingTx tx = AccountingTx.fromProto(proto);

        assertArrayEquals(tx.toProtoMessage().toByteArray(),
                tx.encodeCanonical(CanonicalEncoder.DEFAULT));
        assertArrayEquals(tx.encodeCanonical(CanonicalEncoder.DEFAULT),
                tx.serializeForHash());
    }

    @Test
    public void accountingTxWithDefaultTypeOrdinalEncodeCanonicalMatchesProtobuf() {
        // Type.BTC_TRADE_FEE_TX has ordinal 0, which exercises the proto3 default-value
        // omission path for the `uint32 type = 1` field. Without this case only DPT_TX
        // (ordinal 1) is covered, leaving the omit-when-zero path untested.
        protobuf.AccountingTx proto = protobuf.AccountingTx.newBuilder()
                .setType(AccountingTx.Type.BTC_TRADE_FEE_TX.ordinal())
                .addOutputs(getAccountingTxOutputProto())
                .setTruncatedTxId(ByteString.copyFrom(new byte[]{0x0a, 0x0b, 0x0c, 0x0d}))
                .build();
        AccountingTx tx = AccountingTx.fromProto(proto);

        assertArrayEquals(tx.toProtoMessage().toByteArray(),
                tx.encodeCanonical(CanonicalEncoder.DEFAULT));
        assertArrayEquals(tx.encodeCanonical(CanonicalEncoder.DEFAULT),
                tx.serializeForHash());
    }

    @Test
    public void accountingBlockEncodeCanonicalMatchesProtobuf() {
        protobuf.AccountingBlock proto = protobuf.AccountingBlock.newBuilder()
                .setHeight(1_234_567)
                .setTimeInSec(1_700_000_000)
                .setTruncatedHash(ByteString.copyFrom(new byte[]{0x01, 0x02, 0x03, 0x04}))
                .setTruncatedPreviousBlockHash(ByteString.copyFrom(new byte[]{0x05, 0x06, 0x07, 0x08}))
                .addTxs(getAccountingTxProto())
                .build();
        AccountingBlock block = AccountingBlock.fromProto(proto);

        assertArrayEquals(block.toProtoMessage().toByteArray(),
                block.encodeCanonical(CanonicalEncoder.DEFAULT));
        assertArrayEquals(block.encodeCanonical(CanonicalEncoder.DEFAULT),
                block.serializeForHash());
    }

    private static protobuf.AccountingTx getAccountingTxProto() {
        return protobuf.AccountingTx.newBuilder()
                .setType(AccountingTx.Type.DPT_TX.ordinal())
                .addOutputs(getAccountingTxOutputProto())
                .setTruncatedTxId(ByteString.copyFrom(new byte[]{0x0a, 0x0b, 0x0c, 0x0d}))
                .build();
    }

    private static protobuf.AccountingTxOutput getAccountingTxOutputProto() {
        return protobuf.AccountingTxOutput.newBuilder()
                .setValue(123_456)
                .setName("candidate-name")
                .build();
    }
}
