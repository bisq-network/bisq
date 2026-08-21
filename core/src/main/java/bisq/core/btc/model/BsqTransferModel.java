package bisq.core.btc.model;

import bisq.core.dao.state.model.blockchain.TxType;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import lombok.Getter;

@Getter
public final class BsqTransferModel {

    private final Address receiverAddress;
    private final Coin receiverAmount;
    private final Transaction preparedSendTx;
    private final Transaction txWithBtcFee;
    private final Transaction signedTx;
    private final Coin miningFee;
    private final int txSize;
    private final TxType txType;

    public BsqTransferModel(Address receiverAddress,
                            Coin receiverAmount,
                            Transaction preparedSendTx,
                            Transaction txWithBtcFee,
                            Transaction signedTx) {
        this.receiverAddress = receiverAddress;
        this.receiverAmount = receiverAmount;
        this.preparedSendTx = preparedSendTx;
        this.txWithBtcFee = txWithBtcFee;
        this.signedTx = signedTx;
        this.miningFee = signedTx.getFee();
        this.txSize = signedTx.bitcoinSerialize().length;
        this.txType = TxType.TRANSFER_BSQ;
    }

    public String getReceiverAddressAsString() {
        return receiverAddress.toString();
    }

    public double getTxSizeInKb() {
        return txSize / 1000d;
    }

    public String toShortString() {
        return "{" + "\n" +
                "  receiverAddress='" + getReceiverAddressAsString() + '\'' + "\n" +
                ", receiverAmount=" + receiverAmount + "\n" +
                ", txWithBtcFee.txId=" + txWithBtcFee.getTxId() + "\n" +
                ", miningFee=" + miningFee + "\n" +
                ", txSizeInKb=" + getTxSizeInKb() + "\n" +
                '}';
    }

    @Override
    public String toString() {
        return "BsqTransferModel{" + "\n" +
                "  receiverAddress='" + getReceiverAddressAsString() + '\'' + "\n" +
                ", receiverAmount=" + receiverAmount + "\n" +
                ", preparedSendTx=" + preparedSendTx + "\n" +
                ", txWithBtcFee=" + txWithBtcFee + "\n" +
                ", signedTx=" + signedTx + "\n" +
                ", miningFee=" + miningFee + "\n" +
                ", txSize=" + txSize + "\n" +
                ", txSizeInKb=" + getTxSizeInKb() + "\n" +
                ", txType=" + txType + "\n" +
                '}';
    }
}
