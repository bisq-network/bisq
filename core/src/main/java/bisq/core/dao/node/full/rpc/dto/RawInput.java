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
@JsonPropertyOrder({"txid", "vout", "scriptSig", "txinwitness", "coinbase", "sequence"})
public class RawInput {
    @JsonProperty("txid")
    private String txId;
    @JsonProperty("vout")
    private Integer vOut;
    private SignatureScript scriptSig;
    @JsonProperty("txinwitness")
    private List<String> txInWitness;
    private String coinbase;
    private Long sequence;
}
