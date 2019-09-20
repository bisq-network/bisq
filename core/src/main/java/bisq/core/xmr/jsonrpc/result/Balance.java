package bisq.core.xmr.jsonrpc.result;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Balance implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 135184506420074165L;

	@Expose
	private BigInteger balance; 
	
	@Expose
	@SerializedName(value = "blocks_to_unlock")
	private long blocksToUnlock;

	@Expose
	@SerializedName(value = "multisig_import_needed")
	private boolean multisigImportNeeded;
	
	@Expose
	@SerializedName(value = "unlocked_balance")
	private BigInteger unlockedBalance;

	@Expose
	@SerializedName(value = "per_subaddress")
	private List<SubAddressBalance> perSubaddress;

	@Override
	public String toString() {
		return "Balance [balance=" + balance + ", blocksToUnlock=" + blocksToUnlock + ", multisigImportNeeded="
				+ multisigImportNeeded + ", unlockedBalance=" + unlockedBalance + ", perSubaddress=" + perSubaddress
				+ "]";
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

	public boolean isMultisigImportNeeded() {
		return multisigImportNeeded;
	}

	public void setMultisigImportNeeded(boolean multisigImportNeeded) {
		this.multisigImportNeeded = multisigImportNeeded;
	}

	public BigInteger getUnlockedBalance() {
		return unlockedBalance;
	}

	public void setUnlockedBalance(BigInteger unlockedBalance) {
		this.unlockedBalance = unlockedBalance;
	}

	public List<SubAddressBalance> getPerSubaddress() {
		return perSubaddress;
	}

	public void setPerSubaddress(List<SubAddressBalance> perSubaddress) {
		this.perSubaddress = perSubaddress;
	}
}
