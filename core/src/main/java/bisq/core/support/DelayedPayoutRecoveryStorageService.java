package bisq.core.support;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.persistence.MapStoreService;

import bisq.common.config.Config;
import bisq.common.persistence.PersistenceManager;

import javax.inject.Inject;
import javax.inject.Named;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelayedPayoutRecoveryStorageService extends MapStoreService<DelayedPayoutRecoveryStore, ProtectedStorageEntry> {
    private static final String FILE_NAME = "DelayedPayoutRecoveryStore";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DelayedPayoutRecoveryStorageService(@Named(Config.STORAGE_DIR) File storageDir,
                                      PersistenceManager<DelayedPayoutRecoveryStore> persistenceManager) {
        super(storageDir, persistenceManager);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<DelayedPayoutRecoveryPayload> getDptBackupsDecrypted(int filterMin, int filterMax, byte[] hashOfRecoveryPhrase) {
        List<DelayedPayoutRecoveryPayload> retVal = new ArrayList<>();
        for (ProtectedStorageEntry pse : getMap().values()) {
            DelayedPayoutRecoveryPayload dpt = (DelayedPayoutRecoveryPayload) pse.getProtectedStoragePayload();
            // we're able to filter by locktime from the storage set, as that is the only thing stored non-encrypted.
            // that cuts out the need to attempt decryption of every record.
            if (dpt.getBlockHeight() < filterMin || dpt.getBlockHeight() > filterMax) {
                continue;
            }
            try {   // attempt decryption, which will succeed only for the ones we own.
                SecretKeySpec secret = new SecretKeySpec(hashOfRecoveryPhrase, "AES");
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(dpt.getInitializationVector()));
                dpt.setDecryptedTxBytes(cipher.doFinal(dpt.getEncryptedTxBytes()));
            } catch (Exception ignored) {
                continue;
            }
            retVal.add(dpt);
        }
        return retVal;
    }


    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    @Override
    protected void initializePersistenceManager() {
        persistenceManager.initialize(store, PersistenceManager.Source.NETWORK);
    }

    @Override
    public Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> getMap() {
        return store.getMap();
    }

    @Override
    public boolean canHandle(ProtectedStorageEntry entry) {
        return entry.getProtectedStoragePayload() instanceof DelayedPayoutRecoveryPayload;
    }

    @Override
    protected void readFromResources(String postFix, Runnable completeHandler) {
        // We do not have a resource file for that store, so we just call the readStore method instead.
        readStore(persisted -> completeHandler.run());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected DelayedPayoutRecoveryStore createStore() {
        return new DelayedPayoutRecoveryStore();
    }
}
