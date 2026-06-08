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

package bisq.core.dao.state.model.blockchain;

import bisq.common.encoding.canonical.CanonicalEncoder;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BlockchainCanonicalEncoderTest {
    @Test
    public void txInputEncodeCanonicalMatchesProtobuf() {
        protobuf.TxInput proto = protobuf.TxInput.newBuilder()
                .setConnectedTxOutputTxId("connected-tx")
                .setConnectedTxOutputIndex(2)
                .setPubKey("02abcdef")
                .build();
        TxInput txInput = TxInput.fromProto(proto);

        assertArrayEquals(txInput.toProtoMessage().toByteArray(),
                txInput.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void spentInfoEncodeCanonicalMatchesProtobuf() {
        protobuf.SpentInfo proto = protobuf.SpentInfo.newBuilder()
                .setBlockHeight(12345)
                .setTxId("spending-tx")
                .setInputIndex(3)
                .build();
        SpentInfo spentInfo = SpentInfo.fromProto(proto);

        assertArrayEquals(spentInfo.toProtoMessage().toByteArray(),
                spentInfo.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void txEncodeCanonicalMatchesProtobuf() {
        protobuf.BaseTx proto = getBaseTxProto();
        Tx tx = Tx.fromProto(proto);

        assertArrayEquals(tx.toProtoMessage().toByteArray(),
                tx.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void blockEncodeCanonicalMatchesProtobuf() {
        protobuf.BaseBlock proto = protobuf.BaseBlock.newBuilder()
                .setHeight(654321)
                .setTime(1_700_000_000_000L)
                .setHash("block-hash")
                .setPreviousBlockHash("previous-block-hash")
                .setBlock(protobuf.Block.newBuilder()
                        .addTxs(getBaseTxProto()))
                .build();
        Block block = Block.fromProto(proto);

        assertArrayEquals(block.toProtoMessage().toByteArray(),
                block.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    @Test
    public void txTypeCanonicalCodesMatchProtobuf() {
        assertEquals(protobuf.TxType.PB_ERROR_TX_TYPE.getNumber(), TxType.UNDEFINED.getCode());
        assertEquals(protobuf.TxType.UNDEFINED_TX_TYPE.getNumber(), TxType.UNDEFINED_TX_TYPE.getCode());
        assertEquals(protobuf.TxType.UNVERIFIED.getNumber(), TxType.UNVERIFIED.getCode());
        assertEquals(protobuf.TxType.INVALID.getNumber(), TxType.INVALID.getCode());
        assertEquals(protobuf.TxType.GENESIS.getNumber(), TxType.GENESIS.getCode());
        assertEquals(protobuf.TxType.TRANSFER_BSQ.getNumber(), TxType.TRANSFER_BSQ.getCode());
        assertEquals(protobuf.TxType.PAY_TRADE_FEE.getNumber(), TxType.PAY_TRADE_FEE.getCode());
        assertEquals(protobuf.TxType.PROPOSAL.getNumber(), TxType.PROPOSAL.getCode());
        assertEquals(protobuf.TxType.COMPENSATION_REQUEST.getNumber(), TxType.COMPENSATION_REQUEST.getCode());
        assertEquals(protobuf.TxType.REIMBURSEMENT_REQUEST.getNumber(), TxType.REIMBURSEMENT_REQUEST.getCode());
        assertEquals(protobuf.TxType.BLIND_VOTE.getNumber(), TxType.BLIND_VOTE.getCode());
        assertEquals(protobuf.TxType.VOTE_REVEAL.getNumber(), TxType.VOTE_REVEAL.getCode());
        assertEquals(protobuf.TxType.LOCKUP.getNumber(), TxType.LOCKUP.getCode());
        assertEquals(protobuf.TxType.UNLOCK.getNumber(), TxType.UNLOCK.getCode());
        assertEquals(protobuf.TxType.ASSET_LISTING_FEE.getNumber(), TxType.ASSET_LISTING_FEE.getCode());
        assertEquals(protobuf.TxType.PROOF_OF_BURN.getNumber(), TxType.PROOF_OF_BURN.getCode());
        assertEquals(protobuf.TxType.IRREGULAR.getNumber(), TxType.IRREGULAR.getCode());
    }

    @Test
    public void txOutputTypeCanonicalCodesMatchProtobuf() {
        assertEquals(protobuf.TxOutputType.PB_ERROR_TX_OUTPUT_TYPE.getNumber(), TxOutputType.UNDEFINED.getCode());
        assertEquals(protobuf.TxOutputType.UNDEFINED_OUTPUT.getNumber(), TxOutputType.UNDEFINED_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.GENESIS_OUTPUT.getNumber(), TxOutputType.GENESIS_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.BSQ_OUTPUT.getNumber(), TxOutputType.BSQ_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.BTC_OUTPUT.getNumber(), TxOutputType.BTC_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.PROPOSAL_OP_RETURN_OUTPUT.getNumber(),
                TxOutputType.PROPOSAL_OP_RETURN_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.COMP_REQ_OP_RETURN_OUTPUT.getNumber(),
                TxOutputType.COMP_REQ_OP_RETURN_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.REIMBURSEMENT_OP_RETURN_OUTPUT.getNumber(),
                TxOutputType.REIMBURSEMENT_OP_RETURN_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.CONFISCATE_BOND_OP_RETURN_OUTPUT.getNumber(),
                TxOutputType.CONFISCATE_BOND_OP_RETURN_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.ISSUANCE_CANDIDATE_OUTPUT.getNumber(),
                TxOutputType.ISSUANCE_CANDIDATE_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT.getNumber(),
                TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.BLIND_VOTE_OP_RETURN_OUTPUT.getNumber(),
                TxOutputType.BLIND_VOTE_OP_RETURN_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.VOTE_REVEAL_UNLOCK_STAKE_OUTPUT.getNumber(),
                TxOutputType.VOTE_REVEAL_UNLOCK_STAKE_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT.getNumber(),
                TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.ASSET_LISTING_FEE_OP_RETURN_OUTPUT.getNumber(),
                TxOutputType.ASSET_LISTING_FEE_OP_RETURN_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.PROOF_OF_BURN_OP_RETURN_OUTPUT.getNumber(),
                TxOutputType.PROOF_OF_BURN_OP_RETURN_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.LOCKUP_OUTPUT.getNumber(), TxOutputType.LOCKUP_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.LOCKUP_OP_RETURN_OUTPUT.getNumber(),
                TxOutputType.LOCKUP_OP_RETURN_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.UNLOCK_OUTPUT.getNumber(), TxOutputType.UNLOCK_OUTPUT.getCode());
        assertEquals(protobuf.TxOutputType.INVALID_OUTPUT.getNumber(), TxOutputType.INVALID_OUTPUT.getCode());
    }

    @Test
    public void scriptTypeCanonicalCodesMatchProtobuf() {
        assertEquals(protobuf.ScriptType.PB_ERROR_SCRIPT_TYPES.getNumber(), ScriptType.UNDEFINED.getCode());
        assertEquals(protobuf.ScriptType.PUB_KEY.getNumber(), ScriptType.PUB_KEY.getCode());
        assertEquals(protobuf.ScriptType.PUB_KEY_HASH.getNumber(), ScriptType.PUB_KEY_HASH.getCode());
        assertEquals(protobuf.ScriptType.SCRIPT_HASH.getNumber(), ScriptType.SCRIPT_HASH.getCode());
        assertEquals(protobuf.ScriptType.MULTISIG.getNumber(), ScriptType.MULTISIG.getCode());
        assertEquals(protobuf.ScriptType.NULL_DATA.getNumber(), ScriptType.NULL_DATA.getCode());
        assertEquals(protobuf.ScriptType.WITNESS_V0_KEYHASH.getNumber(), ScriptType.WITNESS_V0_KEYHASH.getCode());
        assertEquals(protobuf.ScriptType.WITNESS_V0_SCRIPTHASH.getNumber(),
                ScriptType.WITNESS_V0_SCRIPTHASH.getCode());
        assertEquals(protobuf.ScriptType.NONSTANDARD.getNumber(), ScriptType.NONSTANDARD.getCode());
        assertEquals(protobuf.ScriptType.WITNESS_UNKNOWN.getNumber(), ScriptType.WITNESS_UNKNOWN.getCode());
        assertEquals(protobuf.ScriptType.WITNESS_V1_TAPROOT.getNumber(), ScriptType.WITNESS_V1_TAPROOT.getCode());
    }

    private static protobuf.BaseTx getBaseTxProto() {
        return protobuf.BaseTx.newBuilder()
                .setTxVersion("2")
                .setId("tx-id")
                .setBlockHeight(654321)
                .setBlockHash("block-hash")
                .setTime(1_700_000_001_000L)
                .addTxInputs(protobuf.TxInput.newBuilder()
                        .setConnectedTxOutputTxId("connected-tx")
                        .setConnectedTxOutputIndex(0)
                        .setPubKey("03abcdef"))
                .setTx(protobuf.Tx.newBuilder()
                        .addTxOutputs(getTxOutputProto())
                        .setTxType(protobuf.TxType.PAY_TRADE_FEE)
                        .setBurntBsq(1234))
                .build();
    }

    private static protobuf.BaseTxOutput getTxOutputProto() {
        return protobuf.BaseTxOutput.newBuilder()
                .setIndex(0)
                .setValue(123_456L)
                .setTxId("tx-id")
                .setPubKeyScript(protobuf.PubKeyScript.newBuilder()
                        .setReqSigs(1)
                        .setScriptType(protobuf.ScriptType.PUB_KEY_HASH)
                        .addAddresses("B1111111111111111111111111111111111")
                        .setAsm("OP_DUP OP_HASH160 abcd OP_EQUALVERIFY OP_CHECKSIG")
                        .setHex("76a914abcd88ac"))
                .setAddress("B1111111111111111111111111111111111")
                .setOpReturnData(ByteString.copyFrom(new byte[]{0x01, 0x02}))
                .setBlockHeight(654321)
                .setTxOutput(protobuf.TxOutput.newBuilder()
                        .setTxOutputType(protobuf.TxOutputType.BSQ_OUTPUT)
                        .setLockTime(-1))
                .build();
    }
}
