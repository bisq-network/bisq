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

package bisq.core.dao.node.full.rpc.dto;

import bisq.core.dao.state.model.blockchain.ScriptType;

import bisq.common.config.Config;
import bisq.common.util.Hex;

import org.bitcoinj.script.Script;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"asm", "hex", "reqSigs", "type", "addresses"})
public class DtoPubKeyScript {
    private String asm;
    private String hex;
    private Integer reqSigs;
    private ScriptType type;
    private List<String> addresses;
    @JsonCreator
    DtoPubKeyScript(
            @JsonProperty("asm") String asm,
            @JsonProperty("hex") String hex,
            @JsonProperty("reqSigs") Integer reqSigs,
            @JsonProperty("type") ScriptType type,
            @JsonProperty("addresses") List<String> addresses) {
        super();
        this.asm = asm;
        this.hex = hex;
        this.reqSigs = reqSigs;
        this.type = type;
        this.addresses = addresses;
        if (addresses == null) {
            // >> addresses are not provided by bitcoin RPC from v22 onwards. <<
            // However they are exported into the DAO classes (and therefore a component of the DAO state hash)
            // so we must generate address from the hex script using BitcoinJ.
            // (n.b. the DAO only ever uses/expects one address)
            try {
                String address = new Script(Hex.decode(hex))
                        .getToAddress(Config.baseCurrencyNetworkParameters()).toString();
                this.addresses = List.of(address);
                this.reqSigs = 1;
            } catch (Exception ex) {
                // certain scripts e.g. OP_RETURN do not resolve to an address
                // in that case do not provide an address to the RawTxOutput
            }
        }
    }
}
