package bisq.core.xmr.jsonrpc.result;

import java.io.Serializable;
import java.math.BigInteger;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class MoneroTransfer implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4955120373028815989L;
	
	@Expose
	private String address;
	
	@Expose
	private BigInteger amount;
	
	@Expose
	private long confirmations;
	
	@Expose
	@SerializedName(value = "double_spend_seen")
	private boolean doubleSpendSeen;
	
	@Expose
	private BigInteger fee;
	
	@Expose
	private long height;

	@Expose
	private String note;
	
	@Expose
	@SerializedName(value = "payment_id")
	private String paymentId;
	
	@Expose
	@SerializedName(value = "subaddr_index")
	private SubAddressIndex subaddrIndex;
	
	@Expose
	@SerializedName(value = "suggested_confirmations_threshold")
	private long suggestedConfirmationsThreshold;
	
	@Expose
	private long timestamp;
	
	@Expose
	@SerializedName(value = "txid")
	private String id;
	
	@Expose
	private String type;
	
	@Expose
	@SerializedName(value = "unlockTime")
	private long unlockTime;

	@Override
	public String toString() {
		return "MoneroTransfer [address=" + address + ", amount=" + amount + ", confirmations=" + confirmations
				+ ", doubleSpendSeen=" + doubleSpendSeen + ", fee=" + fee + ", height=" + height + ", note=" + note
				+ ", paymentId=" + paymentId + ", subaddrIndex=" + subaddrIndex + ", suggestedConfirmationsThreshold="
				+ suggestedConfirmationsThreshold + ", timestamp=" + timestamp + ", id=" + id + ", type=" + type
				+ ", unlockTime=" + unlockTime + "]";
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public BigInteger getAmount() {
		return amount;
	}

	public void setAmount(BigInteger amount) {
		this.amount = amount;
	}

	public long getConfirmations() {
		return confirmations;
	}

	public void setConfirmations(long confirmations) {
		this.confirmations = confirmations;
	}

	public boolean isDoubleSpendSeen() {
		return doubleSpendSeen;
	}

	public void setDoubleSpendSeen(boolean doubleSpendSeen) {
		this.doubleSpendSeen = doubleSpendSeen;
	}

	public BigInteger getFee() {
		return fee;
	}

	public void setFee(BigInteger fee) {
		this.fee = fee;
	}

	public long getHeight() {
		return height;
	}

	public void setHeight(long height) {
		this.height = height;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public String getPaymentId() {
		return paymentId;
	}

	public void setPaymentId(String paymentId) {
		this.paymentId = paymentId;
	}

	public SubAddressIndex getSubaddrIndex() {
		return subaddrIndex;
	}

	public void setSubaddrIndex(SubAddressIndex subaddrIndex) {
		this.subaddrIndex = subaddrIndex;
	}

	public long getSuggestedConfirmationsThreshold() {
		return suggestedConfirmationsThreshold;
	}

	public void setSuggestedConfirmationsThreshold(long suggestedConfirmationsThreshold) {
		this.suggestedConfirmationsThreshold = suggestedConfirmationsThreshold;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public String getId() {
		return id;
	}

	public void setId(String txId) {
		this.id = txId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public long getUnlockTime() {
		return unlockTime;
	}

	public void setUnlockTime(long unlock_time) {
		this.unlockTime = unlock_time;
	}
	
}
