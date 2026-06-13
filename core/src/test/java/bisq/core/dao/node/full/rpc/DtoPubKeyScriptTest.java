package bisq.core.dao.node.full.rpc;

import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.ScriptType;

import bisq.wallets.bitcoind.rpc.responses.BitcoindScriptPubKey;

import com.google.gson.Gson;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoPubKeyScriptTest {
    private static final Gson GSON = new Gson();

    @Test
    void normalizesAnchorScriptToLegacyWitnessUnknown() {
        DtoPubKeyScript scriptPubKey = new DtoPubKeyScript(scriptPubKey(
                "1 29518",
                "51024e73",
                "anchor"));

        assertLegacyPayToAnchorScript(scriptPubKey);
    }

    @Test
    void normalizesPayToAnchorHexEvenWithLegacyScriptType() {
        DtoPubKeyScript scriptPubKey = new DtoPubKeyScript(scriptPubKey(
                "OP_1 0x4e73",
                "51024E73",
                "witness_unknown"));

        assertLegacyPayToAnchorScript(scriptPubKey);
    }

    private void assertLegacyPayToAnchorScript(DtoPubKeyScript scriptPubKey) {
        assertEquals("1 29518", scriptPubKey.getAsm());
        assertEquals("51024e73", scriptPubKey.getHex());
        assertEquals(ScriptType.WITNESS_UNKNOWN, scriptPubKey.getType());
        assertEquals(0, scriptPubKey.getReqSigs());
        assertTrue(scriptPubKey.getAddresses().isEmpty());

        protobuf.PubKeyScript proto = new PubKeyScript(scriptPubKey).toProtoMessage();
        assertEquals(protobuf.ScriptType.WITNESS_UNKNOWN, proto.getScriptType());
        assertEquals(0, proto.getReqSigs());
        assertEquals("1 29518", proto.getAsm());
        assertEquals("51024e73", proto.getHex());
        assertEquals(0, proto.getAddressesCount());
    }

    private BitcoindScriptPubKey scriptPubKey(String asm, String hex, String type) {
        return GSON.fromJson("{\"asm\":\"" + asm + "\"," +
                        "\"hex\":\"" + hex + "\"," +
                        "\"address\":\"bc1pfeessrawgf\"," +
                        "\"type\":\"" + type + "\"}",
                BitcoindScriptPubKey.class);
    }
}
