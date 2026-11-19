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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.parser;

import bisq.core.dao.node.full.RawTxOutput;
import bisq.core.dao.state.model.blockchain.OpReturnType;
import bisq.core.dao.state.model.blockchain.TxOutputType;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpReturnParserTest {
    // The OP_RETURN types with their data length and output type
    private static final Map<Byte, Map.Entry<Integer, TxOutputType>> TYPES = Map.of(
            (byte) 0x10, Map.entry(22, TxOutputType.PROPOSAL_OP_RETURN_OUTPUT),
            (byte) 0x11, Map.entry(22, TxOutputType.COMP_REQ_OP_RETURN_OUTPUT),
            (byte) 0x12, Map.entry(22, TxOutputType.REIMBURSEMENT_OP_RETURN_OUTPUT),
            (byte) 0x13, Map.entry(22, TxOutputType.BLIND_VOTE_OP_RETURN_OUTPUT),
            (byte) 0x14, Map.entry(38, TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT),
            (byte) 0x15, Map.entry(25, TxOutputType.LOCKUP_OP_RETURN_OUTPUT),
            (byte) 0x16, Map.entry(22, TxOutputType.ASSET_LISTING_FEE_OP_RETURN_OUTPUT),
            (byte) 0x17, Map.entry(22, TxOutputType.PROOF_OF_BURN_OP_RETURN_OUTPUT));

    // A new OP_RETURN type needs an entry here, so that everyTypeByteAndLengthGivesAnOutputType fails while the parser
    // has no rule for it and makes its output invalid
    @Test
    public void theTableHasEveryOpReturnType() {
        for (OpReturnType opReturnType : OpReturnType.values()) {
            if (opReturnType != OpReturnType.UNDEFINED) {
                assertTrue(TYPES.containsKey(opReturnType.getType()), opReturnType.name());
            }
        }
    }

    @Test
    public void theTypeByteZeroIsNoOpReturnType() {
        // 0x00 is the byte of OpReturnType.UNDEFINED, the fallback for unknown enum values
        assertTrue(OpReturnType.getOpReturnType((byte) 0x00).isEmpty());
    }

    @Test
    public void everyOtherOpReturnTypeIsFoundByItsByte() {
        for (OpReturnType opReturnType : OpReturnType.values()) {
            if (opReturnType != OpReturnType.UNDEFINED) {
                assertEquals(Optional.of(opReturnType), OpReturnType.getOpReturnType(opReturnType.getType()));
            }
        }
    }

    // The parser threw for the type byte 0x00, which left the block of the transaction half parsed
    @Test
    public void theTypeByteZeroMakesAnInvalidOutput() {
        for (int length : new int[]{22, 25, 38}) {
            assertEquals(TxOutputType.INVALID_OUTPUT, OpReturnParser.getTxOutputType(output(0, data((byte) 0x00, length))));
        }
    }

    // Every type byte and length gives an output type, and none makes the parser throw
    @Test
    public void everyTypeByteAndLengthGivesAnOutputType() {
        for (int typeByte = 0; typeByte < 256; typeByte++) {
            for (int length = 22; length <= 40; length++) {
                Map.Entry<Integer, TxOutputType> type = TYPES.get((byte) typeByte);
                TxOutputType expected = type != null && type.getKey() == length ?
                        type.getValue() :
                        TxOutputType.INVALID_OUTPUT;
                assertEquals(expected, OpReturnParser.getTxOutputType(output(0, data((byte) typeByte, length))),
                        "type byte " + typeByte + ", length " + length);
            }
        }
    }

    @Test
    public void dataWithValueOrShorterThan22BytesMakesAnInvalidOutput() {
        assertEquals(TxOutputType.INVALID_OUTPUT, OpReturnParser.getTxOutputType(output(1, data((byte) 0x10, 22))));
        assertEquals(TxOutputType.INVALID_OUTPUT, OpReturnParser.getTxOutputType(output(0, data((byte) 0x10, 21))));
    }

    // Type, version, and for a lockup the reason BONDED_ROLE and the lock time 1. The other types ignore these bytes.
    private static byte[] data(byte type, int length) {
        byte[] data = new byte[length];
        data[0] = type;
        data[1] = 0x01;
        data[2] = 0x01;
        data[4] = 0x01;
        return data;
    }

    private static TempTxOutput output(long value, byte[] opReturnData) {
        return TempTxOutput.fromRawTxOutput(new RawTxOutput(0, value, "txId", null, null, opReturnData, 1));
    }
}
