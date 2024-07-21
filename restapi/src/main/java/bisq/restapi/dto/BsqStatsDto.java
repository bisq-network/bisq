package bisq.restapi.dto;

import lombok.Value;

@Value
public class BsqStatsDto {
    long minted;
    long burnt;
    int addresses;
    int unspent_txos;
    int spent_txos;
    int height;
    int genesisHeight;
}
