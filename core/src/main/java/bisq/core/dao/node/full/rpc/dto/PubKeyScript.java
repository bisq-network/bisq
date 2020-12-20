package bisq.core.dao.node.full.rpc.dto;

import bisq.core.dao.state.model.blockchain.ScriptType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"asm", "hex", "reqSigs", "type", "addresses"})
public class PubKeyScript {
    private String asm;
    private String hex;
    private Integer reqSigs;
    private ScriptType type;
    private List<String> addresses;
}
