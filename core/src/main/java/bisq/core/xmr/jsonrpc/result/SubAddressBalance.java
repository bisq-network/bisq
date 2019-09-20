package bisq.core.xmr.jsonrpc.result;

import java.io.Serializable;
import java.math.BigInteger;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class SubAddressBalance implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5840586182676538682L;

	@Expose
	@SerializedName(value = "account_index")
	private int accountIndex;
	
	@Expose
	private String address;
	
	@Expose
	@SerializedName(value = "address_index")
	private int addressIndex;
	
	@Expose
	private BigInteger balance;
	
	@Expose
	@SerializedName(value = "blocks_to_unlock")
	private long blocksToUnlock;
	
	@Expose
	private String label;
	
	@Expose
	@SerializedName(value = "num_unspent_outputs")
	private int numUnSpentOutputs;
	
	@Expose
	@SerializedName(value = "unlocked_balance")
	private BigInteger unlockedBalance;

	@Override
	public String toString() {
		return "SubAddressBalance [accountIndex=" + accountIndex + ", address=" + address + ", addressIndex="
				+ addressIndex + ", balance=" + balance + ", blocksToUnlock=" + blocksToUnlock + ", label=" + label
				+ ", numUnSpentOutputs=" + numUnSpentOutputs + ", unlockedBalance=" + unlockedBalance + "]";
	}

	public int getAccountIndex() {
		return accountIndex;
	}

	public void setAccountIndex(int accountIndex) {
		this.accountIndex = accountIndex;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public int getAddressIndex() {
		return addressIndex;
	}

	public void setAddressIndex(int addressIndex) {
		this.addressIndex = addressIndex;
	}

	public BigInteger getBalance() {
		return balance;
	}

	public void setBalance(BigInteger balance) {
		this.balance = balance;
	}

	public long getBlocksToUnlock() {
		return blocksToUnlock;
	}

	public void setBlocksToUnlock(long blocksToUnlock) {
		this.blocksToUnlock = blocksToUnlock;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public int getNumUnSpentOutputs() {
		return numUnSpentOutputs;
	}

	public void setNumUnSpentOutputs(int numUnSpentOutputs) {
		this.numUnSpentOutputs = numUnSpentOutputs;
	}

	public BigInteger getUnlockedBalance() {
		return unlockedBalance;
	}

	public void setUnlockedBalance(BigInteger unlockedBalance) {
		this.unlockedBalance = unlockedBalance;
	}
}
