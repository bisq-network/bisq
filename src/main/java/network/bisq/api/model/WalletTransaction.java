package network.bisq.api.model;

public class WalletTransaction {

    public long updateTime;
    public String hash;
    public long fee;
    public long value;
    public long valueSentToMe;
    public long valueSentFromMe;
    public int confirmations;
    public boolean inbound;
    public String address;
}
