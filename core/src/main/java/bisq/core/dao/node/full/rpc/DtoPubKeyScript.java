package bisq.core.dao.node.full.rpc;

import bisq.core.dao.state.model.blockchain.ScriptType;

import bisq.common.config.Config;
import bisq.common.util.Hex;

import org.bitcoinj.script.Script;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.Getter;



import bisq.wallets.bitcoind.rpc.responses.BitcoindScriptPubKey;

@Getter
public class DtoPubKeyScript {
    // Pay-to-anchor (P2A) script: OP_1 followed by the two-byte witness program 0x4e73.
    // Bitcoin Core 29 reports this script as type "anchor", but legacy nodes did not.
    private static final String PAY_TO_ANCHOR_HEX = "51024e73";
    // Verified with Bitcoin Knots v27.1.knots20240801:
    // `decodescript 51024e73` returns asm "1 29518" and type "witness_unknown".
    // Keep this legacy asm because PubKeyScript.asm is serialized into the DAO state hash.
    private static final String PAY_TO_ANCHOR_LEGACY_ASM = "1 29518";

    private final String asm;
    private final String hex;
    private final ScriptType type;
    private final int reqSigs;
    private final List<String> addresses;

    public DtoPubKeyScript(BitcoindScriptPubKey bitcoindScriptPubKey) {
        if (isPayToAnchor(bitcoindScriptPubKey)) {
            // Bitcoin Core 29 returns type "anchor" for the P2A script, while older Core versions returned
            // "witness_unknown". The DAO state hash must keep the older representation.
            this.asm = PAY_TO_ANCHOR_LEGACY_ASM;
            this.hex = PAY_TO_ANCHOR_HEX;
            this.type = ScriptType.WITNESS_UNKNOWN;
            this.addresses = Collections.emptyList();
            this.reqSigs = 0;
            return;
        }

        this.asm = bitcoindScriptPubKey.getAsm();
        this.hex = bitcoindScriptPubKey.getHex();
        this.type = ScriptType.fromScriptPubKey(bitcoindScriptPubKey);

        // >> addresses are not provided by bitcoin RPC from v22 onwards. <<
        // However they are exported into the DAO classes (and therefore a component of the DAO state hash)
        // so we must generate address from the hex script using BitcoinJ.
        // (n.b. the DAO only ever uses/expects one address)
        Optional<String> address = computeAddressFromScriptHex();
        if (address.isPresent()) {
            this.addresses = List.of(address.get());
            this.reqSigs = 1;
        } else {
            this.addresses = Collections.emptyList();
            this.reqSigs = 0;
        }
    }

    private Optional<String> computeAddressFromScriptHex() {
        try {
            String address = new Script(Hex.decode(hex))
                    .getToAddress(Config.baseCurrencyNetworkParameters()).toString();
            return Optional.of(address);

        } catch (Exception ex) {
            // certain scripts e.g. OP_RETURN do not resolve to an address
            // in that case do not provide an address to the RawTxOutput
            return Optional.empty();
        }
    }

    private boolean isPayToAnchor(BitcoindScriptPubKey bitcoindScriptPubKey) {
        return PAY_TO_ANCHOR_HEX.equalsIgnoreCase(bitcoindScriptPubKey.getHex()) ||
                "anchor".equals(bitcoindScriptPubKey.getType());
    }
}
