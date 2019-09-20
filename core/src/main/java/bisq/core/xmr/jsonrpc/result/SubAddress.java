package bisq.core.xmr.jsonrpc.result;

import java.io.Serializable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class SubAddress implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6809755448109678848L;
	
	@Expose
	private String address;
	
	@Expose
	@SerializedName(value = "address_index")
	private int addressIndex;
	
	@Expose
	private String label;
	
	@Expose
	private boolean used;

	@Override
	public String toString() {
		return "SubAddress [address=" + address + ", addressIndex=" + addressIndex + ", label=" + label + ", used="
				+ used + "]";
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

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public boolean isUsed() {
		return used;
	}

	public void setUsed(boolean used) {
		this.used = used;
	}

}
