package bisq.core.xmr.wallet;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InetAddresses;

import bisq.asset.CryptoNoteAddressValidator;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.xmr.wallet.listeners.WalletBalanceListener;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import lombok.extern.slf4j.Slf4j;
import monero.rpc.MoneroRpcConnection;
import monero.wallet.MoneroWalletRpc;
import monero.wallet.model.MoneroSendPriority;
import monero.wallet.model.MoneroSendRequest;
import monero.wallet.model.MoneroTxSet;
import monero.wallet.model.MoneroTxWallet;

@Slf4j
@Singleton
public class XmrWalletRpcWrapper {
	public static String HOST = "127.0.0.1";
	public static int PORT = 29088;
	protected final Logger log = LoggerFactory.getLogger(this.getClass());
	private MoneroWalletRpc walletRpc;
	private boolean xmrWalletRpcRunning = false;
	private String primaryAddress;
	
	//TODO(niyid) onChangeListener to dynamically create and set new walletRpc instance.
	//TODO(niyid) onChangeListener fires only after any of host, port, user, password have changed
	//TODO(niyid) Only allow testnet, stagenet connections in dev/test. Only mainnet allowed in prod.

    @Inject
    public XmrWalletRpcWrapper(Preferences preferences) {
		log.debug("instantiating MoneroWalletRpc...");
		HOST = preferences.getXmrUserHostDelegate();
		PORT = Integer.parseInt(preferences.getXmrHostPortDelegate());
        //TODO(niyid) Use preferences to determine which wallet to load in XmrWalletRpcWrapper		
		openWalletRpcInstance(null);
		if(xmrWalletRpcRunning) {
			//TODO(niyid) Uncomment later
/**/			
			CryptoNoteAddressValidator validator;
			long[] validPrefixes = {};
			if(DevEnv.isDevMode()) {
            	validPrefixes = new long[]{24, 36, 53, 63};
            	validator = new CryptoNoteAddressValidator(true, validPrefixes);
			} else {
            	validPrefixes = new long[]{18, 42};
            	validator = new CryptoNoteAddressValidator(true, validPrefixes);
			}
			if(!validator.validate(primaryAddress).isValid()) {
				log.debug("Wallet RPC Connection not valid (MAINNET/TESTNET mix-up); shutting down...");
				xmrWalletRpcRunning = false;
				walletRpc.close();
				walletRpc = null;
			}
/**/			
		}
    }
        
    public void update(WalletBalanceListener listener, HashMap<String, Object> walletRpcData) { 
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
		    	checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
		    	listener.playAnimation();
				if(walletRpcData != null) {
					long time0;
					if(walletRpcData.containsKey("getBalance")) {
						time0 = System.currentTimeMillis();
						BigInteger balance = walletRpc.getBalance();
						walletRpcData.put("getBalance", balance);
						log.debug("listen -time: {}ms - balance: {}", (System.currentTimeMillis() - time0), balance);
					}
					if(walletRpcData.containsKey("getUnlockedBalance")) {
						time0 = System.currentTimeMillis();
						BigInteger unlockedBalance = walletRpc.getUnlockedBalance();
						walletRpcData.put("getUnlockedBalance", unlockedBalance);
						log.debug("listen -time: {}ms - unlockedBalance: {}", (System.currentTimeMillis() - time0));
					}
					if(walletRpcData.containsKey("getPrimaryAddress")) {
						time0 = System.currentTimeMillis();
						primaryAddress = walletRpc.getPrimaryAddress();
						walletRpcData.put("getPrimaryAddress", primaryAddress);
						log.debug("listen -time: {}ms - address: {}", (System.currentTimeMillis() - time0), primaryAddress);
					}
					if(walletRpcData.containsKey("getTxs")) {
						time0 = System.currentTimeMillis();
						List<MoneroTxWallet> txList = walletRpc.getTxs();
						if(txList != null && !txList.isEmpty()) {
							walletRpcData.put("getTxs", transformTxWallet(txList));
							log.debug("listen -time: {}ms - transactions: {}", (System.currentTimeMillis() - time0), txList.size());
						} else {
							List<XmrTxListItem> list = Collections.emptyList();
							walletRpcData.put("getTxs", list);
						}
					}
				}
				listener.onUpdateBalances(walletRpcData);
				listener.stopAnimation();
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
			listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}
    }
    
    private List<XmrTxListItem> transformTxWallet(List<MoneroTxWallet> txList) {
		Predicate<MoneroTxWallet> predicate = new Predicate<>() {

			@Override
			public boolean test(MoneroTxWallet t) {
				return t.isRelayed();
			}
		};
    	List<XmrTxListItem> list = new ArrayList<>();
		txList.stream().filter(predicate).forEach(txWallet -> list.add(new XmrTxListItem(txWallet)));
    	log.debug("transformTxWallet => {}", list.size());

    	return list.size() > 100 ? list.subList(0, 100) : list;//Reduce transactions to no more than 100.
    }
    
    public void searchTx(WalletBalanceListener listener, String commaSeparatedIds) {
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
		    	checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
				HashMap<String, Object> walletRpcData = new HashMap<>();
				listener.playAnimation();
				if(commaSeparatedIds != null && !commaSeparatedIds.isEmpty()) {
					String searchParam = commaSeparatedIds.replaceAll(" ", "");
					
					searchParam.split(",");
					long time0 = System.currentTimeMillis();
					List<MoneroTxWallet> txs = walletRpc.getTxs(Arrays.asList(searchParam.split(",")));
					walletRpcData.put("getTxs", transformTxWallet(txs));
					log.debug("listen -time: {}ms - searchTx: {}", (System.currentTimeMillis() - time0), txs.size());
				}
				listener.onUpdateBalances(walletRpcData);
				listener.stopAnimation();
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
        	listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}
    }
    
    public void createTx(WalletBalanceListener listener, Integer accountIndex, String address, 
    		BigInteger amount, MoneroSendPriority priority, boolean doNotRelay, HashMap<String, Object> walletRpcData) { 
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
				checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
				listener.playAnimation();
				long time0 = System.currentTimeMillis();
				MoneroSendRequest request = new MoneroSendRequest(accountIndex, address, amount, priority);
				request.setDoNotRelay(doNotRelay);
				MoneroTxSet txSet = walletRpc.send(request);
				
				if(txSet != null && txSet.getTxs() != null && !txSet.getTxs().isEmpty()) {
					MoneroTxWallet tx = txSet.getTxs().get(0);
					walletRpcData.put("getBalance", walletRpc.getBalance());
					walletRpcData.put("getUnlockedBalance", walletRpc.getUnlockedBalance());
					walletRpcData.put("getDoNotRelay", tx.getDoNotRelay());
					walletRpcData.put("getExtra", tx.getExtra());
					walletRpcData.put("getFee", tx.getFee());
					walletRpcData.put("getId", tx.getId());
					walletRpcData.put("getKey", tx.getKey());
					walletRpcData.put("getLastRelayedTimestamp", tx.getLastRelayedTimestamp() != null ? new Date(tx.getLastRelayedTimestamp()) : null);
					walletRpcData.put("getMixin", tx.getMixin());
					walletRpcData.put("getNumConfirmations", tx.getNumConfirmations());
					walletRpcData.put("getOutgoingAmount", tx.getOutgoingAmount());
					walletRpcData.put("getOutgoingTransfer", tx.getOutgoingTransfer());
					walletRpcData.put("getPaymentId", tx.getPaymentId());
					walletRpcData.put("getTimestamp",  tx.getBlock().getTimestamp() != null ? new Date(tx.getBlock().getTimestamp()) : null);
					walletRpcData.put("getSize", tx.getSize());
					walletRpcData.put("getUnlockTime", tx.getUnlockTime());
					walletRpcData.put("getVersion", tx.getVersion());
					if(doNotRelay) {
						walletRpcData.put("txToRelay", tx);
					}
					log.debug("MoneroTxWallet => {}", walletRpcData);
					log.debug("createTx -time: {}ms - createTx: {}", (System.currentTimeMillis() - time0), tx.getSize());
				}
				listener.onUpdateBalances(walletRpcData);
				listener.stopAnimation();
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
			listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}
    }
    
    public void relayTx(WalletBalanceListener listener, HashMap<String, Object> walletRpcData) { 
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
				checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
				listener.playAnimation();
				MoneroTxWallet txToRelay = (MoneroTxWallet) walletRpcData.get("txToRelay");
				if(txToRelay != null) {
					txToRelay.setDoNotRelay(false);
					long time0 = System.currentTimeMillis();
					String txId = walletRpc.relayTx(txToRelay);
					walletRpcData.put("txId", txId);
					walletRpcData.put("getMetadata", txToRelay.getMetadata());
					log.debug("relayTx metadata: {}", txToRelay.getMetadata());
					log.debug("relayTx -time: {}ms - txId: {}", (System.currentTimeMillis() - time0), txId);
				}
				listener.stopAnimation();
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
			listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}
    }

	public void fetchLanguages(WalletBalanceListener listener, ComboBox<String> languageComboBox) {
    	log.debug("createWalletRpcInstance - {}, {}");
        ObservableList<String> languageOptions = FXCollections.observableArrayList();
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
				checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
        		languageOptions.addAll(walletRpc.getLanguages());
        		languageComboBox.setItems(languageOptions);
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
			listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}
	}
    
    public void openWalletRpcInstance(WalletBalanceListener listener) {
    	log.debug("openWalletRpcInstance - {}, {}", HOST, PORT);
        Thread checkIfXmrLocalHostNodeIsRunningThread = new Thread(() -> {
            Thread.currentThread().setName("checkIfXmrLocalHostNodeIsRunningThread");
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(InetAddresses.forString(HOST), PORT), 5000);
                log.debug("Localhost Monero Wallet RPC detected.");
                UserThread.execute(() -> {
                	xmrWalletRpcRunning = true;
            		walletRpc = new MoneroWalletRpc(new MoneroRpcConnection("http://" + HOST + ":" + PORT));
                });
            } catch (Throwable e) {
            	log.debug("createWalletRpcInstance - {}", e.getMessage());
            	e.printStackTrace();
            	if(listener != null) {
           			listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed", "Monero", e.getLocalizedMessage()));
            	}
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        });
        checkIfXmrLocalHostNodeIsRunningThread.start();
    }
    
    public boolean isXmrWalletRpcRunning() {
    	return xmrWalletRpcRunning;
    }

	public void createWalletRpcInstance(WalletBalanceListener listener, String walletFile, String password, String language, TextArea seedWordsTextArea) {
    	log.debug("createWalletRpcInstance - {}, {}, {}, {}", HOST, PORT, walletFile, password);
        Thread checkIfXmrLocalHostNodeIsRunningThread = new Thread(() -> {
            Thread.currentThread().setName("checkIfXmrLocalHostNodeIsRunningThread");
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(InetAddresses.forString(HOST), PORT), 5000);
                log.debug("Localhost Monero Wallet RPC detected.");
                UserThread.execute(() -> {
                	xmrWalletRpcRunning = true;
            		walletRpc = new MoneroWalletRpc(new MoneroRpcConnection("http://" + HOST + ":" + PORT));
            		walletRpc.createWalletRandom(walletFile, password, language);
            		String mnemonic = walletRpc.getMnemonic();
            		seedWordsTextArea.setText(mnemonic);
                });
            } catch (Throwable e) {
            	log.debug("createWalletRpcInstance - {}", e.getMessage());
            	e.printStackTrace();
            	if(listener != null) {
                	listener.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed", "Monero", e.getLocalizedMessage()));
            	}
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        });
        checkIfXmrLocalHostNodeIsRunningThread.start();
	}
	
	public void handleTxProof(TxProofHandler handler, String txId, String message) {
		Runnable command = new Runnable() {
			
			@Override
			public void run() {
				checkNotNull(walletRpc, Res.get("mainView.networkWarning.localhostLost", "Monero"));
				handler.playAnimation();
				if(handler != null) {
					long time0 = System.currentTimeMillis();
					String signature = walletRpc.getSpendProof(txId, message);
					log.debug("relayTx -time: {}ms - txId: {}", (System.currentTimeMillis() - time0), txId);
					log.debug("relayTx signature: {}", signature);
					handler.update(txId, message, signature);
				}
				handler.stopAnimation();
			}
		};
		try {
			Platform.runLater(command);
		} catch (Exception e) {
			handler.popupErrorWindow(Res.get("shared.account.wallet.popup.error.startupFailed"));
		}		
	}

	public MoneroWalletRpc getWalletRpc() {
    	return walletRpc;
    }
	
	public String getPrimaryAddress() {
		if(primaryAddress == null) {
			walletRpc = new MoneroWalletRpc(new MoneroRpcConnection("http://" + HOST + ":" + PORT));
			primaryAddress = walletRpc.getPrimaryAddress();
		}
		return primaryAddress;
	}
}
