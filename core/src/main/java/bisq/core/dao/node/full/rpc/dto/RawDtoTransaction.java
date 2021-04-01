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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"txid", "hash", "version", "size", "vsize", "weight", "locktime", "vin", "vout", "hex"})
public class RawDtoTransaction {
    @JsonProperty("in_active_chain")
    private Boolean inActiveChain;
    @JsonProperty("txid")
    private String txId;
    private String hash;
    private Integer version;
    private Integer size;
    @JsonProperty("vsize")
    private Integer vSize;
    private Integer weight;
    @JsonProperty("locktime")
    private Long lockTime;
    @JsonProperty("vin")
    private List<RawDtoInput> vIn;
    @JsonProperty("vout")
    private List<RawDtoOutput> vOut;
    @JsonProperty("blockhash")
    private String blockHash;
    private Integer confirmations;
    @JsonProperty("blocktime")
    private Long blockTime;
    private Long time;
    private String hex;

    @JsonCreator
    public static Summarized summarized(String hex) {
        var result = new Summarized();
        result.setHex(hex);
        return result;
    }

    public static class Summarized extends RawDtoTransaction {
        @Override
        @JsonValue
        public String getHex() {
            return super.getHex();
        }
    }
}
