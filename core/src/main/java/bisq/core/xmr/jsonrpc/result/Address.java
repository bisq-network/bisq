package bisq.core.xmr.jsonrpc.result;

import java.io.Serializable;
import java.util.List;

import com.google.gson.annotations.Expose;

public class Address implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6025042322196110971L;

	@Expose
	private String address;
	
	@Expose
	private List<SubAddress> addresses;

	@Override
	public String toString() {
		return "Address [address=" + address + ", addresses=" + addresses + "]";
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public List<SubAddress> getAddresses() {
		return addresses;
	}

	public void setAddresses(List<SubAddress> addresses) {
		this.addresses = addresses;
	}
	
}
