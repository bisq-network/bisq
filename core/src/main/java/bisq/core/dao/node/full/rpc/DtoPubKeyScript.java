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
    private final String asm;
    private final String hex;
    private final ScriptType type;
    private final int reqSigs;
    private final List<String> addresses;

    public DtoPubKeyScript(BitcoindScriptPubKey bitcoindScriptPubKey) {
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
}
