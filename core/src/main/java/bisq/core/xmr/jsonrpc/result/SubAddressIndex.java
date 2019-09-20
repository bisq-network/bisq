package bisq.core.xmr.jsonrpc.result;

import java.io.Serializable;

import com.google.gson.annotations.Expose;

public class SubAddressIndex implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -887528883772924454L;
	
	@Expose
	private long major;
	
	@Expose
	private long minor;

	public long getMajor() {
		return major;
	}

	public void setMajor(long major) {
		this.major = major;
	}

	public long getMinor() {
		return minor;
	}

	public void setMinor(long minor) {
		this.minor = minor;
	}

	@Override
	public String toString() {
		return "SubAddressIndex [major=" + major + ", minor=" + minor + "]";
	}

}
