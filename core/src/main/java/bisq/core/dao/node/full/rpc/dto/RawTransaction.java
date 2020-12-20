package bisq.core.dao.node.full.rpc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"txid", "hash", "version", "size", "vsize", "weight", "locktime", "vin", "vout", "hex"})
public class RawTransaction {
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
    private List<RawInput> vIn;
    @JsonProperty("vout")
    private List<RawOutput> vOut;
    @JsonProperty("blockhash")
    private String blockHash;
    private Integer confirmations;
    @JsonProperty("blocktime")
    private Long blockTime;
    private Long time;
    private String hex;
}
