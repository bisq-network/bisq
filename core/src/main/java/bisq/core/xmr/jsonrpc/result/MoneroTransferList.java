package bisq.core.xmr.jsonrpc.result;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import com.google.gson.annotations.Expose;

public class MoneroTransferList implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2239920012741769530L;

	@Expose
	private List<MoneroTransfer> in = Collections.emptyList();
	
	@Expose
	private List<MoneroTransfer> out = Collections.emptyList();
	
	@Expose
	private List<MoneroTransfer> pending = Collections.emptyList();
	
	@Expose
	private List<MoneroTransfer> failed = Collections.emptyList();
	
	@Expose
	private List<MoneroTransfer> pool = Collections.emptyList();

	@Override
	public String toString() {
		return "MoneroTransferList [in=" + in + ", out=" + out + ", pending=" + pending + ", failed=" + failed
				+ ", pool=" + pool + "]";
	}

	public List<MoneroTransfer> getIn() {
		return in;
	}

	public void setIn(List<MoneroTransfer> in) {
		this.in = in;
	}

	public List<MoneroTransfer> getOut() {
		return out;
	}

	public void setOut(List<MoneroTransfer> out) {
		this.out = out;
	}

	public List<MoneroTransfer> getPending() {
		return pending;
	}

	public void setPending(List<MoneroTransfer> pending) {
		this.pending = pending;
	}

	public List<MoneroTransfer> getFailed() {
		return failed;
	}

	public void setFailed(List<MoneroTransfer> failed) {
		this.failed = failed;
	}

	public List<MoneroTransfer> getPool() {
		return pool;
	}

	public void setPool(List<MoneroTransfer> pool) {
		this.pool = pool;
	}
}
