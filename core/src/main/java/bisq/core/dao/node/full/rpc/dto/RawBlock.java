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
@JsonIgnoreProperties(ignoreUnknown = true, value = "ntx")
@JsonPropertyOrder({"hash", "confirmations", "strippedsize", "size", "weight", "height", "version", "versionHex",
        "merkleroot", "tx", "time", "mediantime", "nonce", "bits", "difficulty", "chainwork", "nTx",
        "previousblockhash", "nextblockhash"})
public class RawBlock {
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
    private List<RawTransaction> tx;
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

//    public RawBlock(String hash, Integer confirmations, Integer strippedSize, Integer size, Integer weight,
//                    Integer height, Integer version, String versionHex, String merkleRoot, List<RawTransaction> tx,
//                    Long time, Long medianTime, Long nonce, String bits, BigDecimal difficulty, String chainWork,
//                    Integer nTx, String previousBlockHash, String nextBlockHash) {
//        setHash(hash);
//        setConfirmations(confirmations);
//        setStrippedSize(strippedSize);
//        setSize(size);
//        setWeight(weight);
//        setHeight(height);
//        setVersion(version);
//        setVersionHex(versionHex);
//        setMerkleRoot(merkleRoot);
//        setTx(tx);
//        setTime(time);
//        setMedianTime(medianTime);
//        setNonce(nonce);
//        setBits(bits);
//        setDifficulty(difficulty);
//        setChainWork(chainWork);
//        setNTx(nTx);
//        setPreviousBlockHash(previousBlockHash);
//        setNextBlockHash(nextBlockHash);
//    }
//
//    public void setDifficulty(BigDecimal difficulty) {
//        this.difficulty = difficulty.setScale(8, RoundingMode.HALF_UP);
//    }
}
