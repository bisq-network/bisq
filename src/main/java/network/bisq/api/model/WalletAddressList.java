package network.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class WalletAddressList {

    public List<WalletAddress> walletAddresses = new ArrayList<>();

    public long total;
}
