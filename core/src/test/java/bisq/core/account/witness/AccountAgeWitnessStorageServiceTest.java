package bisq.core.account.witness;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.PersistableNetworkPayloadStore;

import bisq.common.app.Version;

import java.nio.file.Path;

import java.io.File;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.is;


public class AccountAgeWitnessStorageServiceTest {
    private DummyHistoricalDataStoreService storageService;

    @BeforeEach
    void setup(@TempDir Path tempDirPath) {
        File tempDir = tempDirPath.toFile();
        storageService = new DummyHistoricalDataStoreService(tempDir);
    }

    @Test
    void emptyStore() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion(Version.VERSION);
        assertThat(mapSinceVersion, is(anEmptyMap()));
    }

    @Test
    void testOnlyLiveData() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap = storageService.getStore().getMap();
        DummyAccountAgeWitnessFactory.addNewAccountAgeWitnessesToMap(liveDataMap, 2);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion(Version.VERSION);

        P2PDataStorage.ByteArray firstByteArray = new P2PDataStorage.ByteArray(new byte[]{0});
        P2PDataStorage.ByteArray secondByteArray = new P2PDataStorage.ByteArray(new byte[]{1});

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = Map.of(
                firstByteArray, liveDataMap.get(firstByteArray),
                secondByteArray, liveDataMap.get(secondByteArray));

        assertThat(mapSinceVersion, is(expected));
    }

    @Test
    void testOnlyStoreData() {
        AccountAgeWitnessStore versionStore = DummyAccountAgeWitnessFactory
                .createAccountAgeWitnessStore(1, 100);

        Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storeByVersion = Map.of(
                "1.8.0", versionStore);

        storageService.setStoresByVersion(storeByVersion);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion("1.7.0");

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = new HashMap<>(versionStore.getMap());
        assertThat(mapSinceVersion, is(expected));
    }

    @Test
    void testRequesterVersionNull() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap = storageService.getStore().getMap();
        DummyAccountAgeWitnessFactory.addNewAccountAgeWitnessToMap(liveDataMap);

        AccountAgeWitnessStore firstVersionStore = DummyAccountAgeWitnessFactory
                .createAccountAgeWitnessStore(1, 100);
        AccountAgeWitnessStore secondVersionStore = DummyAccountAgeWitnessFactory
                .createAccountAgeWitnessStore(1, 200);

        Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storeByVersion = Map.of(
                "1.8.0", firstVersionStore,
                "1.9.0", secondVersionStore);

        storageService.setStoresByVersion(storeByVersion);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion(null);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = new HashMap<>();
        expected.putAll(liveDataMap);
        expected.putAll(firstVersionStore.getMap());
        expected.putAll(secondVersionStore.getMap());

        assertThat(mapSinceVersion, is(expected));
    }

    @Test
    void testRequesterVersionIsOlder() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap = storageService.getStore().getMap();
        DummyAccountAgeWitnessFactory.addNewAccountAgeWitnessToMap(liveDataMap);

        AccountAgeWitnessStore firstVersionStore = DummyAccountAgeWitnessFactory.
                createAccountAgeWitnessStore(1, 100);
        AccountAgeWitnessStore secondVersionStore = DummyAccountAgeWitnessFactory.
                createAccountAgeWitnessStore(1, 200);

        Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storeByVersion = Map.of(
                "1.8.0", firstVersionStore,
                "1.9.0", secondVersionStore);

        storageService.setStoresByVersion(storeByVersion);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion("1.8.5");

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = new HashMap<>();
        expected.putAll(liveDataMap);
        expected.putAll(secondVersionStore.getMap());

        assertThat(mapSinceVersion, is(expected));
    }

    @Test
    void testRequesterVersionIsNewer() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap = storageService.getStore().getMap();
        DummyAccountAgeWitnessFactory.addNewAccountAgeWitnessToMap(liveDataMap);

        AccountAgeWitnessStore firstVersionStore = DummyAccountAgeWitnessFactory.
                createAccountAgeWitnessStore(1, 100);
        AccountAgeWitnessStore secondVersionStore = DummyAccountAgeWitnessFactory.
                createAccountAgeWitnessStore(1, 200);

        Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storeByVersion = Map.of(
                "1.8.0", firstVersionStore,
                "1.9.0", secondVersionStore);

        storageService.setStoresByVersion(storeByVersion);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion("1.9.5");

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = new HashMap<>(liveDataMap);
        assertThat(mapSinceVersion, is(expected));
    }
}
