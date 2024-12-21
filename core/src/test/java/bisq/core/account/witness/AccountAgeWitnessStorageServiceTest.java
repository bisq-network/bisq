package bisq.core.account.witness;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.PersistableNetworkPayloadStore;

import bisq.common.app.Version;

import java.nio.file.Path;

import java.io.File;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.is;


public class AccountAgeWitnessStorageServiceTest {
    private final AtomicBoolean payloadsTruncated = new AtomicBoolean(false);
    private DummyHistoricalDataStoreService storageService;

    @BeforeEach
    void setup(@TempDir Path tempDirPath) {
        File tempDir = tempDirPath.toFile();
        storageService = new DummyHistoricalDataStoreService(tempDir);
    }

    @Test
    void emptyStore() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion(Version.VERSION, Collections.emptySet(), payloadsTruncated);
        assertThat(mapSinceVersion, is(anEmptyMap()));
        assertThat(payloadsTruncated.get(), is(false));
    }

    @Test
    void testOnlyLiveData() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap = storageService.getStore().getMap();
        DummyAccountAgeWitnessFactory.addNewAccountAgeWitnessesToMap(liveDataMap, 2);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion(Version.VERSION, Collections.emptySet(), payloadsTruncated);

        P2PDataStorage.ByteArray firstByteArray = new P2PDataStorage.ByteArray(new byte[]{0});
        P2PDataStorage.ByteArray secondByteArray = new P2PDataStorage.ByteArray(new byte[]{1});

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = Map.of(
                firstByteArray, liveDataMap.get(firstByteArray),
                secondByteArray, liveDataMap.get(secondByteArray));

        assertThat(mapSinceVersion, is(expected));
        assertThat(payloadsTruncated.get(), is(false));
    }

    @Test
    void testOnlyStoreData() {
        AccountAgeWitnessStore versionStore = DummyAccountAgeWitnessFactory
                .createAccountAgeWitnessStore(1, 100);

        Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storeByVersion = Map.of(
                "1.8.0", versionStore);

        storageService.setStoresByVersion(storeByVersion);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion("1.7.0", Collections.emptySet(), payloadsTruncated);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = new HashMap<>(versionStore.getMap());
        assertThat(mapSinceVersion, is(expected));
        assertThat(payloadsTruncated.get(), is(false));
    }

    @Test
    void testRequesterVersionNullCappedLiveData() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap = storageService.getStore().getMap();
        DummyAccountAgeWitnessFactory.addNewAccountAgeWitnessesToMap(liveDataMap, 150);

        AccountAgeWitnessStore firstVersionStore = DummyAccountAgeWitnessFactory
                .createAccountAgeWitnessStore(1, 200);
        AccountAgeWitnessStore secondVersionStore = DummyAccountAgeWitnessFactory
                .createAccountAgeWitnessStore(1, 300);

        Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storeByVersion = Map.of(
                "1.8.0", firstVersionStore,
                "1.9.0", secondVersionStore);

        storageService.setStoresByVersion(storeByVersion);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion(null, Collections.emptySet(), payloadsTruncated);

        int mapSize = mapSinceVersion.size();
        assertThat(mapSize, is(100));

        for (Map.Entry<P2PDataStorage.ByteArray, PersistableNetworkPayload> entry : mapSinceVersion.entrySet()) {
            var expected = liveDataMap.get(entry.getKey());
            assertThat(entry.getValue(), is(expected));
        }

        assertThat(payloadsTruncated.get(), is(false));
    }

    @Test
    void testRequesterUncappedData() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap = storageService.getStore().getMap();
        DummyAccountAgeWitnessFactory.addNewAccountAgeWitnessesToMap(liveDataMap, 150);

        AccountAgeWitnessStore firstVersionStore = DummyAccountAgeWitnessFactory
                .createAccountAgeWitnessStore(1, 2_000);
        AccountAgeWitnessStore secondVersionStore = DummyAccountAgeWitnessFactory
                .createAccountAgeWitnessStore(1, 3_000);

        Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storeByVersion = Map.of(
                "1.8.0", firstVersionStore,
                "1.9.0", secondVersionStore);

        storageService.setStoresByVersion(storeByVersion);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion("1.2.3", Collections.emptySet(), payloadsTruncated);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = new HashMap<>();
        expected.putAll(liveDataMap);
        expected.putAll(firstVersionStore.getMap());
        expected.putAll(secondVersionStore.getMap());

        assertThat(mapSinceVersion, is(expected));
        assertThat(payloadsTruncated.get(), is(false));
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
                storageService.getMapSinceVersion("1.8.5", Collections.emptySet(), payloadsTruncated);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = new HashMap<>();
        expected.putAll(liveDataMap);
        expected.putAll(secondVersionStore.getMap());

        assertThat(mapSinceVersion, is(expected));
        assertThat(payloadsTruncated.get(), is(false));
    }

    @Test
    void testRequesterHasHistoricalDataStores() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap = storageService.getStore().getMap();
        DummyAccountAgeWitnessFactory.addNewAccountAgeWitnessesToMap(liveDataMap, 150);

        AccountAgeWitnessStore firstVersionStore = DummyAccountAgeWitnessFactory.
                createAccountAgeWitnessStore(1, 200);
        AccountAgeWitnessStore secondVersionStore = DummyAccountAgeWitnessFactory.
                createAccountAgeWitnessStore(1, 300);

        Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storeByVersion = Map.of(
                "1.8.0", firstVersionStore,
                "1.9.0", secondVersionStore);

        storageService.setStoresByVersion(storeByVersion);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion("1.9.5", Collections.emptySet(), payloadsTruncated);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = new HashMap<>(liveDataMap);
        assertThat(mapSinceVersion, is(expected));
        assertThat(payloadsTruncated.get(), is(false));
    }

    @Test
    void testRequesterVersionIsNewerCapped() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap = storageService.getStore().getMap();
        DummyAccountAgeWitnessFactory.addNewAccountAgeWitnessesToMap(liveDataMap, 150);

        AccountAgeWitnessStore firstVersionStore = DummyAccountAgeWitnessFactory
                .createAccountAgeWitnessStore(1, 200);
        AccountAgeWitnessStore secondVersionStore = DummyAccountAgeWitnessFactory
                .createAccountAgeWitnessStore(1, 300);

        Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storeByVersion = Map.of(
                "1.8.0", firstVersionStore,
                "1.9.0", secondVersionStore);

        storageService.setStoresByVersion(storeByVersion);

        String version = Version.VERSION;
        String higherVersion = Version.getMajorVersion(version) + "." +
                Version.getMinorVersion(version) + "." + (Version.getPatchVersion(version) + 1);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion(higherVersion, Collections.emptySet(), payloadsTruncated);

        int mapSize = mapSinceVersion.size();
        assertThat(mapSize, is(100));

        for (Map.Entry<P2PDataStorage.ByteArray, PersistableNetworkPayload> entry : mapSinceVersion.entrySet()) {
            var expected = liveDataMap.get(entry.getKey());
            assertThat(entry.getValue(), is(expected));
        }

        assertThat(payloadsTruncated.get(), is(true));
    }

    @Test
    void testKnownHashes() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap = storageService.getStore().getMap();
        DummyAccountAgeWitnessFactory.addNewAccountAgeWitnessesToMap(liveDataMap, 3);

        AccountAgeWitnessStore firstVersionStore = DummyAccountAgeWitnessFactory.
                createAccountAgeWitnessStore(4, 100);
        AccountAgeWitnessStore secondVersionStore = DummyAccountAgeWitnessFactory.
                createAccountAgeWitnessStore(5, 200);

        Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storeByVersion = Map.of(
                "1.8.0", firstVersionStore,
                "1.9.0", secondVersionStore);

        storageService.setStoresByVersion(storeByVersion);

        P2PDataStorage.ByteArray excludeFromLiveMap = liveDataMap.keySet().stream()
                .findAny()
                .orElseThrow();

        P2PDataStorage.ByteArray excludeFromSecondStore = secondVersionStore.getMap()
                .keySet().stream()
                .findAny()
                .orElseThrow();

        Set<P2PDataStorage.ByteArray> knownHashes = Set.of(excludeFromLiveMap, excludeFromSecondStore);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion("1.7.0", knownHashes, payloadsTruncated);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = new HashMap<>(liveDataMap);
        expected.putAll(firstVersionStore.getMap());
        expected.putAll(secondVersionStore.getMap());

        expected.remove(excludeFromLiveMap);
        expected.remove(excludeFromSecondStore);

        assertThat(mapSinceVersion, is(expected));
        assertThat(payloadsTruncated.get(), is(false));
    }

    @Test
    void testExcludeAllHashes() {
        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> liveDataMap = storageService.getStore().getMap();
        DummyAccountAgeWitnessFactory.addNewAccountAgeWitnessesToMap(liveDataMap, 3);

        AccountAgeWitnessStore firstVersionStore = DummyAccountAgeWitnessFactory.
                createAccountAgeWitnessStore(4, 100);
        AccountAgeWitnessStore secondVersionStore = DummyAccountAgeWitnessFactory.
                createAccountAgeWitnessStore(5, 200);

        Map<String, PersistableNetworkPayloadStore<? extends PersistableNetworkPayload>> storeByVersion = Map.of(
                "1.8.0", firstVersionStore,
                "1.9.0", secondVersionStore);

        storageService.setStoresByVersion(storeByVersion);

        Set<P2PDataStorage.ByteArray> knownHashes = new HashSet<>(liveDataMap.keySet());
        Set<P2PDataStorage.ByteArray> secondStoreKeys = secondVersionStore.getMap().keySet();
        knownHashes.addAll(secondStoreKeys);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> mapSinceVersion =
                storageService.getMapSinceVersion("1.8.5", knownHashes, payloadsTruncated);

        Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> expected = Collections.emptyMap();
        assertThat(mapSinceVersion, is(expected));
        assertThat(payloadsTruncated.get(), is(false));
    }
}
