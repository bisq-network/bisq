package bisq.core.xmr.jsonrpc;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import bisq.core.xmr.jsonrpc.result.Address;
import bisq.core.xmr.jsonrpc.result.Balance;
import bisq.core.xmr.jsonrpc.result.MoneroTransfer;
import bisq.core.xmr.jsonrpc.result.MoneroTransferList;
import bisq.core.xmr.jsonrpc.result.MoneroTx;

public class MoneroWalletRpc {
	
	protected Logger log = LoggerFactory.getLogger(MoneroWalletRpc.class);
	
	private static final Gson GSON = new Gson();
	
	private MoneroRpcConnection rpcConnection; 

	public MoneroWalletRpc(MoneroRpcConnection rpcConnection) {
		this.rpcConnection = rpcConnection;
	}
	
	public String getPrimaryAddress() {
		Map<String, Object> params = new HashMap<>();
		params.put("account_index", 0);
		Map<String, Object> response = rpcConnection.sendJsonRequest("get_address", params);
		log.debug("response => {}", response);
		
		Address address = GSON.fromJson(GSON.toJson(response.get("result")), Address.class);
		log.debug("address => {}", address);
		
		return address.getAddress();
	}
	
	public Balance getBalanceData() {
		Map<String, Object> response = rpcConnection.sendJsonRequest("get_balance");
		log.debug("response => {}", response);
		
		Balance balance = GSON.fromJson(GSON.toJson(response.get("result")), Balance.class);
		log.debug("balance => {}", balance);
		
		return balance;
	}
	
	public BigInteger getBalance() {
		return getBalanceData().getBalance();
	}
	
	public BigInteger getUnlockedBalance() {
		return getBalanceData().getUnlockedBalance();
	}
	
	public MoneroTx send(Map<String, Object> request) {
		Map<String, Object> response = rpcConnection.sendJsonRequest("transfer", request);
		log.debug("response => {}", response);

		MoneroTx moneroTx = GSON.fromJson(GSON.toJson(response.get("result")), MoneroTx.class);
		log.debug("moneroTx => {}", moneroTx);
		
		return moneroTx;
	}
	
	@SuppressWarnings("unchecked")
	public String relayTx(String txMetadata) {
		Map<String, Object> params = new HashMap<>();
		params.put("hex", txMetadata);
		Map<String, Object> response = rpcConnection.sendJsonRequest("relay_tx", params);
		log.debug("response => {}", response);
		
		Map<String, Object> result = (Map<String, Object>) response.get("result");
		String txHash = (String) result.get("tx_hash");
		log.debug("txHash => {}", txHash);
		
		return txHash;
	}
	
	public List<MoneroTransfer> getTxs(String txIds) {
		Map<String, Object> params = new HashMap<>();
		Map<String, Object> response = null;
		List<MoneroTransfer> transfers = new ArrayList<>();
		if(txIds == null || txIds.isEmpty()) {
			params.put("in", true);
			params.put("out", true);
			params.put("pending", true);
			params.put("failed", true);
			params.put("pool", true);
			params.put("in", true);
			params.put("in", true);
			params.put("in", true);
			response = rpcConnection.sendJsonRequest("get_transfers", params);	
			log.debug("response => {}", response);
			MoneroTransferList transferList = GSON.fromJson(GSON.toJson(response.get("result")), MoneroTransferList.class);
			transfers.addAll(transferList.getIn());
			transfers.addAll(transferList.getOut());
			transfers.addAll(transferList.getPending());
			transfers.addAll(transferList.getPool());
			transfers.addAll(transferList.getFailed());
		} else {
			String[] txIdTokens = txIds.replace(" ", "").split(",") ;
			for(String tid : txIdTokens) {
				params = new HashMap<>();
				params.put("txid", tid);
				response = rpcConnection.sendJsonRequest("get_transfer_by_txid", params);
				log.debug("response => {}", response);
				MoneroTransfer transfer = GSON.fromJson(GSON.toJson(response.get("transfer")), MoneroTransfer.class);
				transfers.add(transfer);
			}
		}
		
		log.debug("transfers => {}", transfers);
		
		return transfers;
	}
	
	@SuppressWarnings("unchecked")
	public String getSpendProof(String txId, String message) {
		Map<String, Object> params = new HashMap<>();
		params.put("txid", txId);
		if(message != null && !message.isEmpty() ) {
			params.put("message", message);
		}
		Map<String, Object> response = rpcConnection.sendJsonRequest("get_spend_proof", params);
		log.debug("response => {}", response);

		Map<String, Object> result = (Map<String, Object>) response.get("result");
		String signature = (String) result.get("signature");
		log.debug("signature => {}", signature);
		
		return signature;
		
	}
	
	@SuppressWarnings("unchecked")
	public boolean checkSpendProof(String txId, String message, String signature) {
		Map<String, Object> params = new HashMap<>();
		params.put("txid", txId);
		if(message != null && !message.isEmpty() ) {
			params.put("message", message);
		}
		params.put("signature", signature);
		Map<String, Object> response = rpcConnection.sendJsonRequest("get_spend_proof", params);
		log.debug("response => {}", response);

		Map<String, Object> result = (Map<String, Object>) response.get("result");
		boolean good = (boolean) result.get("good");
		log.debug("good => {}", good);
		
		return good;
		
	}
	
	public static final String generatePaymentId() {
		String f32 = UUID.randomUUID().toString().replace("-", "");
		String l32 = UUID.randomUUID().toString().replace("-", "");
		return f32.concat(l32);		
	}
	
	public void close() {
		Map<String, Object> response = rpcConnection.sendJsonRequest("close_wallet");
		log.debug("response => {}", response);
	}

}
