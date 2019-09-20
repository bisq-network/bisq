package bisq.core.xmr.jsonrpc;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import bisq.core.xmr.jsonrpc.result.MoneroTransfer;
import bisq.core.xmr.jsonrpc.result.MoneroTx;

public class MoneroRpcConnectionTest {
	private Logger log = LoggerFactory.getLogger(MoneroRpcConnectionTest.class);
	public static final Gson GSON = new Gson();

	@Test
	@Ignore
	public void testExecute() {
		MoneroRpcConnection connection = new MoneroRpcConnection("http://localhost:29088", "rpc_user", "rpcpassword123");
		MoneroWalletRpc walletRpc = new MoneroWalletRpc(connection);
		
		log.info("walletRpc.getPrimaryAddress => {}", walletRpc.getPrimaryAddress());
		
		log.info("walletRpc.getBalance => {}", walletRpc.getBalance());
		
		log.info("walletRpc.getUnlockedBalance => {}", walletRpc.getUnlockedBalance());
		
//		Map<String, Object> destination = new HashMap<>();
//		destination.put("amount", new BigInteger("1300000000000"));
//		destination.put("address", "A19Nu2WbrA2f8aJrqJAjLh45mw7Nuft3BCnNBv2a4u3qigMR1ytdgGJLoJLzF6PkQe1Cs36CxagmoKbSTCPMgQ7eCFhFTiy");
//		List<Map<String, Object>> destinations = new ArrayList<Map<String,Object>>();
//		destinations.add(destination);
//		Map<String, Object> request = new HashMap<>();
//		request.put("destinations", destinations);
//		request.put("priority", MoneroSendPriority.NORMAL.ordinal());
//		request.put("payment_id", MoneroWalletRpc.generatePaymentId());
//		request.put("get_tx_key", true);
//		request.put("get_tx_hex", false);
//		request.put("do_not_relay", true);
//		request.put("get_tx_metadata", true);

//		MoneroTx moneroTx = walletRpc.send(request);
//		log.info("walletRpc.send => {}", moneroTx);
//		
//		log.info("walletRpc.txHash => {}", walletRpc.relayTx(moneroTx.getTxMetadata()));
		
		String txId = "fb43267b69c165f8143a599b58254ed5b695dac3fa778266b78f75e2c611ed1e";
		String message = "One of the incoming transactions";
		String signature = walletRpc.getSpendProof(txId, message);
		log.info("walletRpc.spendProof => {}", signature);
		
		boolean good = walletRpc.checkSpendProof(txId, message, signature);
		log.info("walletRpc.checkSpendProof => {}", good);
	}
}
