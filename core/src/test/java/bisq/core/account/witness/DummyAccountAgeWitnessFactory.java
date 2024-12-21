package bisq.core.account.witness;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import java.util.Map;

public class DummyAccountAgeWitnessFactory {

    static AccountAgeWitnessStore createAccountAgeWitnessStore(int numberOfWitnesses, int startKeyOffset) {
        AccountAgeWitnessStore accountAgeWitnessStore = new AccountAgeWitnessStore();
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> storeMap = accountAgeWitnessStore.getMap();
        addNewAccountAgeWitnessesToMap(storeMap, numberOfWitnesses, startKeyOffset);
        return accountAgeWitnessStore;
    }

    static void addNewAccountAgeWitnessesToMap(Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap,
                                                int numberOfWitnesses) {
        addNewAccountAgeWitnessesToMap(liveDataMap, numberOfWitnesses, 0);
    }

    private static void addNewAccountAgeWitnessesToMap(Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap,
                                                int numberOfWitnesses,
                                                int startKeyOffset) {
        for (int i = 0; i < numberOfWitnesses; i++) {
            addNewAccountAgeWitnessToMap(liveDataMap, startKeyOffset + i);
        }
    }

    static void addNewAccountAgeWitnessToMap(Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap) {
        addNewAccountAgeWitnessToMap(liveDataMap, liveDataMap.size());
    }

    private static void addNewAccountAgeWitnessToMap(Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap, int key) {
        P2PDataStorage.ByteArray byteArray = new P2PDataStorage.ByteArray(new byte[]{(byte) key});
        AccountAgeWitness accountAgeWitness = new AccountAgeWitness(new byte[]{(byte) key}, key + 1);
        liveDataMap.put(byteArray, accountAgeWitness);
    }
}
