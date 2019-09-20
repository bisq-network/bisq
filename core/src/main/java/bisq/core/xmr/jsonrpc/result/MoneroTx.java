package bisq.core.xmr.jsonrpc.result;

import java.io.Serializable;
import java.math.BigInteger;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class MoneroTx implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4941720175150037660L;
	
	@Expose
	private BigInteger amount;

	private BigInteger fee;
	
	@Expose
	@SerializedName(value = "multisig_txset")
	private String multisigTxset;
	
	@Expose
	@SerializedName(value = "tx_blob")
	private String txBlob;
	
	@Expose
	@SerializedName(value = "tx_hash")
	private String txHash;
	
	@Expose
	@SerializedName(value = "tx_key")
	private String txKey;
	
	@Expose
	@SerializedName(value = "tx_metadata")
	private String txMetadata;
	
	@Expose
	@SerializedName(value = "get_tx_metadata")
	private boolean getTxMetadata;
	
	@Expose
	@SerializedName(value = "unsigned_txset")
	private String unsignedTxset;
	
	@Expose
	private long size;

	@Override
	public String toString() {
		return "MoneroTx [amount=" + amount + ", fee=" + fee + ", multisigTxset=" + multisigTxset + ", txBlob=" + txBlob
				+ ", txHash=" + txHash + ", txKey=" + txKey + ", txMetadata=" + txMetadata + ", getTxMetadata="
				+ getTxMetadata + ", unsignedTxset=" + unsignedTxset + "]";
	}

	public BigInteger getAmount() {
		return amount;
	}

	public void setAmount(BigInteger amount) {
		this.amount = amount;
	}

	public BigInteger getFee() {
		return fee;
	}

	public void setFee(BigInteger fee) {
		this.fee = fee;
	}

	public String getMultisigTxset() {
		return multisigTxset;
	}

	public void setMultisigTxset(String multisigTxset) {
		this.multisigTxset = multisigTxset;
	}

	public String getTxBlob() {
		return txBlob;
	}

	public void setTxBlob(String txBlob) {
		this.txBlob = txBlob;
	}

	public String getTxHash() {
		return txHash;
	}

	public void setTxHash(String txHash) {
		this.txHash = txHash;
	}

	public String getTxKey() {
		return txKey;
	}

	public void setTxKey(String txKey) {
		this.txKey = txKey;
	}

	public String getTxMetadata() {
		return txMetadata;
	}

	public void setTxMetadata(String txMetadata) {
		this.txMetadata = txMetadata;
	}

	public boolean isGetTxMetadata() {
		return getTxMetadata;
	}

	public void setGetTxMetadata(boolean getTxMetadata) {
		this.getTxMetadata = getTxMetadata;
	}

	public String getUnsignedTxset() {
		return unsignedTxset;
	}

	public void setUnsignedTxset(String unsignedTxset) {
		this.unsignedTxset = unsignedTxset;
	}
	
	public long getSize() {
		return txMetadata != null ? txMetadata.getBytes().length : 0;
	}
}
