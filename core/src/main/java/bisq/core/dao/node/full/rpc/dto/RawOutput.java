package bisq.core.dao.node.full.rpc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"value", "n", "scriptPubKey"})
public class RawOutput {
    private Double value;
    private Integer n;
    private PubKeyScript scriptPubKey;

//    public RawOutput(BigDecimal value, Integer n, PubKeyScript scriptPubKey) {
//        setValue(value);
//        setN(n);
//        setScriptPubKey(scriptPubKey);
//    }
//
//    public void setValue(BigDecimal value) {
//        this.value = value.setScale(8, RoundingMode.HALF_UP);
//    }
}
