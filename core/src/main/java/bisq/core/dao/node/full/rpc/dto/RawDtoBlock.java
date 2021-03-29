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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true, value = "ntx")
@JsonPropertyOrder({"hash", "confirmations", "strippedsize", "size", "weight", "height", "version", "versionHex",
        "merkleroot", "tx", "time", "mediantime", "nonce", "bits", "difficulty", "chainwork", "nTx",
        "previousblockhash", "nextblockhash"})
public class RawDtoBlock {
    private String hash;
    private Integer confirmations;
    @JsonProperty("strippedsize")
    private Integer strippedSize;
    private Integer size;
    private Integer weight;
    private Integer height;
    private Integer version;
    private String versionHex;
    @JsonProperty("merkleroot")
    private String merkleRoot;
    private List<RawDtoTransaction> tx;
    private Long time;
    @JsonProperty("mediantime")
    private Long medianTime;
    private Long nonce;
    private String bits;
    private Double difficulty;
    @JsonProperty("chainwork")
    private String chainWork;
    // There seems to be a bug in Jackson where it misses and/or duplicates this field without
    // an explicit @JsonProperty annotation plus the @JsonIgnoreProperties 'ntx' term above:
    @JsonProperty("nTx")
    private Integer nTx;
    @JsonProperty("previousblockhash")
    private String previousBlockHash;
    @JsonProperty("nextblockhash")
    private String nextBlockHash;

    @JsonCreator
    public static Summarized summarized(String hex) {
        var result = new Summarized();
        result.setHex(hex);
        return result;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Summarized extends RawDtoBlock {
        @Getter(onMethod_ = @JsonValue)
        private String hex;
    }
}
