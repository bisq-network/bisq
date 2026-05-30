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

package bisq.core.dao.monitoring.serialization;

import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.ScriptType;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.ParamChange;
import bisq.core.dao.state.model.governance.Vote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden hex vectors locking the on-the-wire encoding of every
 * {@link CanonicalWriter} primitive and every {@link CanonicalLeafWriter}
 * encoder.
 *
 * <p>Each test computes the canonical bytes for a fixed input and asserts
 * against a hardcoded hex string. A change to the format (intentional or
 * otherwise) will fail one of these tests immediately, before it can drift
 * into the DAO state hash and fork the network. To bless a deliberate
 * format change: run the test, copy the produced hex into the assertion,
 * then go re-verify any related hash chain.
 */
public class CanonicalWriterGoldenTest {

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static CanonicalWriter w() {
        return CanonicalWriter.intoMemory();
    }

    private interface LeafFn {
        void run(CanonicalWriter cw);
    }

    private static String leaf(LeafFn fn) {
        CanonicalWriter cw = w();
        fn.run(cw);
        return hex(cw.toByteArray());
    }

    // ---------- primitives ----------

    @Test
    public void varint_zero_one_127_128_16384() {
        // Boundary values of unsigned LEB128:
        // 0      -> 00
        // 1      -> 01
        // 127    -> 7f         (one-byte max)
        // 128    -> 80 01      (two-byte minimum)
        // 16384  -> 80 80 01   (three-byte minimum)
        assertEquals("00", hex(w().writeVarint(0).toByteArray()));
        assertEquals("01", hex(w().writeVarint(1).toByteArray()));
        assertEquals("7f", hex(w().writeVarint(127).toByteArray()));
        assertEquals("8001", hex(w().writeVarint(128).toByteArray()));
        assertEquals("808001", hex(w().writeVarint(16384).toByteArray()));
    }

    @Test
    public void varintLong_large_values() {
        // 2^32 = 0x1_0000_0000: needs five 7-bit groups
        // 10 00 00 00 10 in little-endian 7-bit groups -> 80 80 80 80 10
        assertEquals("80808080 10".replace(" ", ""),
                hex(w().writeVarintLong(1L << 32).toByteArray()));
        // Long.MAX_VALUE = 0x7FFF_FFFF_FFFF_FFFF: nine bytes of 7f with a 7f terminator
        assertEquals("ffffffffffffffff7f",
                hex(w().writeVarintLong(Long.MAX_VALUE).toByteArray()));
    }

    @Test
    public void i32_big_endian() {
        assertEquals("00000000", hex(w().writeI32(0).toByteArray()));
        assertEquals("00000001", hex(w().writeI32(1).toByteArray()));
        assertEquals("7fffffff", hex(w().writeI32(Integer.MAX_VALUE).toByteArray()));
        assertEquals("80000000", hex(w().writeI32(Integer.MIN_VALUE).toByteArray()));
        assertEquals("ffffffff", hex(w().writeI32(-1).toByteArray()));
    }

    @Test
    public void i64_big_endian() {
        assertEquals("0000000000000000", hex(w().writeI64(0L).toByteArray()));
        assertEquals("0000000000000001", hex(w().writeI64(1L).toByteArray()));
        assertEquals("7fffffffffffffff", hex(w().writeI64(Long.MAX_VALUE).toByteArray()));
        assertEquals("8000000000000000", hex(w().writeI64(Long.MIN_VALUE).toByteArray()));
    }

    @Test
    public void bool_one_byte() {
        assertEquals("00", hex(w().writeBool(false).toByteArray()));
        assertEquals("01", hex(w().writeBool(true).toByteArray()));
    }

    @Test
    public void bytes_length_prefixed() {
        // Empty: varint(0) = 00
        assertEquals("00", hex(w().writeBytes(new byte[0]).toByteArray()));
        // One byte 0xab: varint(1) = 01, then ab -> 01ab
        assertEquals("01ab", hex(w().writeBytes(new byte[]{(byte) 0xab}).toByteArray()));
        // 128 bytes of 0x00: varint(128) = 80 01, then 128 zero bytes
        byte[] big = new byte[128];
        StringBuilder expected = new StringBuilder("8001");
        for (int i = 0; i < 128; i++) expected.append("00");
        assertEquals(expected.toString(), hex(w().writeBytes(big).toByteArray()));
    }

    @Test
    public void string_utf8_length_prefixed() {
        // Empty
        assertEquals("00", hex(w().writeString("").toByteArray()));
        // ASCII "abc" -> varint(3)=03 + 61 62 63
        assertEquals("03616263", hex(w().writeString("abc").toByteArray()));
        // Non-ASCII: "ä" is U+00E4, UTF-8 = C3 A4 (2 bytes). varint(2)=02 + c3 a4
        assertEquals("02c3a4", hex(w().writeString("ä").toByteArray()));
        // CJK: "中" is U+4E2D, UTF-8 = E4 B8 AD (3 bytes). varint(3)=03 + e4 b8 ad
        assertEquals("03e4b8ad", hex(w().writeString("中").toByteArray()));
    }

    @Test
    public void optional_presence_byte() {
        assertEquals("00", hex(w().writeOptionalString(null).toByteArray()));
        // present: 01 then varint(0)=00 for empty string -> 0100
        assertEquals("0100", hex(w().writeOptionalString("").toByteArray()));
        assertEquals("0101ab", hex(w().writeOptionalBytes(new byte[]{(byte) 0xab}).toByteArray()));
    }

    @Test
    public void list_size_prefixed() {
        // Empty list of strings: varint(0)=00
        assertEquals("00", hex(w().writeStringList(java.util.Collections.emptyList()).toByteArray()));
        // ["a","b"]: varint(2)=02, then each string
        assertEquals("0201610162",
                hex(w().writeStringList(java.util.Arrays.asList("a", "b")).toByteArray()));
    }

    @Test
    public void sortedMap_iterates_by_key_natural_order() {
        // TreeMap iterates by natural order regardless of insertion order
        java.util.TreeMap<String, String> tm = new java.util.TreeMap<>();
        tm.put("b", "2");
        tm.put("a", "1");
        // Expect: varint(2)=02, "a"(0161), "1"(0131), "b"(0162), "2"(0132)
        assertEquals("020161013101620132",
                hex(w().writeSortedMap(tm, CanonicalWriter::writeString, CanonicalWriter::writeString).toByteArray()));
    }

    // ---------- enum codes ----------

    @Test
    public void enum_codes_are_locked() {
        // Locks every enum-to-code mapping. Any reorder/rename of these
        // enums will fail one of these assertions before forking consensus.
        assertEquals(0, CanonicalLeafWriter.codeOf(IssuanceType.UNDEFINED));
        assertEquals(1, CanonicalLeafWriter.codeOf(IssuanceType.COMPENSATION));
        assertEquals(2, CanonicalLeafWriter.codeOf(IssuanceType.REIMBURSEMENT));

        assertEquals(0, CanonicalLeafWriter.codeOf(DaoPhase.Phase.UNDEFINED));
        assertEquals(1, CanonicalLeafWriter.codeOf(DaoPhase.Phase.PROPOSAL));
        assertEquals(2, CanonicalLeafWriter.codeOf(DaoPhase.Phase.BREAK1));
        assertEquals(3, CanonicalLeafWriter.codeOf(DaoPhase.Phase.BLIND_VOTE));
        assertEquals(4, CanonicalLeafWriter.codeOf(DaoPhase.Phase.BREAK2));
        assertEquals(5, CanonicalLeafWriter.codeOf(DaoPhase.Phase.VOTE_REVEAL));
        assertEquals(6, CanonicalLeafWriter.codeOf(DaoPhase.Phase.BREAK3));
        assertEquals(7, CanonicalLeafWriter.codeOf(DaoPhase.Phase.RESULT));

        // ScriptType codes mirror proto field numbers
        assertEquals(0, CanonicalLeafWriter.codeOf(ScriptType.UNDEFINED));
        assertEquals(1, CanonicalLeafWriter.codeOf(ScriptType.PUB_KEY));
        assertEquals(10, CanonicalLeafWriter.codeOf(ScriptType.WITNESS_V1_TAPROOT));
    }

    // ---------- leaf encoders ----------

    @Test
    public void txInput_golden() {
        TxInput input = new TxInput("aabb", 3, "ff");
        // Encoding:
        //   string("aabb") = varint(4)=04 + 61 61 62 62
        //   i32(3)         = 00 00 00 03
        //   optional string("ff") = 01 + varint(2)=02 + 66 66
        String expected = "0461616262" + "00000003" + "01" + "026666";
        assertEquals(expected, leaf(cw -> CanonicalLeafWriter.writeTxInput(cw, input)));
    }

    @Test
    public void spentInfo_golden() {
        SpentInfo info = new SpentInfo(700_000L, "ab", 1);
        // i64(700000) + string("ab") + i32(1)
        String expected = "00000000000aae60" + "026162" + "00000001";
        assertEquals(expected, leaf(cw -> CanonicalLeafWriter.writeSpentInfo(cw, info)));
    }

    @Test
    public void txOutputKey_golden() {
        TxOutputKey key = new TxOutputKey("aa", 5);
        // string("aa") = 02 61 61 ; i32(5) = 00 00 00 05
        assertEquals("0261610000 0005".replace(" ", ""),
                leaf(cw -> CanonicalLeafWriter.writeTxOutputKey(cw, key)));
    }

    @Test
    public void vote_golden() {
        Vote vote = new Vote(true);
        assertEquals("01", leaf(cw -> CanonicalLeafWriter.writeVote(cw, vote)));
    }

    @Test
    public void daoPhase_golden() {
        DaoPhase phase = new DaoPhase(DaoPhase.Phase.PROPOSAL, 10);
        // enumCode(1) = 01 ; i32(10) = 00 00 00 0a
        assertEquals("010000000a", leaf(cw -> CanonicalLeafWriter.writeDaoPhase(cw, phase)));
    }

    @Test
    public void paramChange_golden() {
        ParamChange paramChange = new ParamChange("PROPOSAL_FEE", "100", 700_000);
        // string("PROPOSAL_FEE")=0c+ascii ; string("100")=03+ascii ; i32(700000)=00 0a ae 60
        String expected = "0c" + "50524f504f53414c5f464545"
                + "03" + "313030"
                + "000aae60";
        assertEquals(expected, leaf(cw -> CanonicalLeafWriter.writeParamChange(cw, paramChange)));
    }

    @Test
    public void pubKeyScript_golden() {
        PubKeyScript script = PubKeyScript.fromProto(protobuf.PubKeyScript.newBuilder()
                .setReqSigs(1)
                .setScriptType(protobuf.ScriptType.PUB_KEY)
                .addAddresses("addr1")
                .setAsm("asm-body")
                .setHex("deadbeef")
                .build());
        // i32(1) + enum(1)=01 + list[1, "addr1"] + string("asm-body") + string("deadbeef")
        String expected = "00000001"
                + "01"
                + "01" + "05" + "6164647231"
                + "08" + "61736d2d626f6479"
                + "08" + "6465616462656566";
        assertEquals(expected, leaf(cw -> CanonicalLeafWriter.writePubKeyScript(cw, script)));
    }

    @Test
    public void domainSeparator_in_full_serializer() {
        // The serializer always prefixes the domain separator + version tag
        // before any state bytes. Quickly assert presence; full encoding is
        // covered by the DAO-level determinism tests + mainnet byte-equality.
        String prefix = hex(CanonicalDaoStateSerializer.DOMAIN_SEPARATOR);
        // ASCII: "BISQ_HASH_PREIMAGE\0v2\0DAO_STATE_HASH_CHAIN\0"
        String expectedPrefix =
                "424953515f484153485f505245494d414745" + "00"
              + "7632" + "00"
              + "44414f5f53544154455f484153485f434841494e" + "00";
        assertEquals(expectedPrefix, prefix);
        assertEquals(0x02, CanonicalDaoStateSerializer.VERSION_TAG);
    }
}
