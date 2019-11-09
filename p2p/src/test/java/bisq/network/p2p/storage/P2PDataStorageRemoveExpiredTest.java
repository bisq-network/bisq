package bisq.network.p2p.storage;

import bisq.network.p2p.TestUtils;
import bisq.network.p2p.storage.mocks.ExpirableProtectedStoragePayloadStub;
import bisq.network.p2p.storage.mocks.PersistableExpirableProtectedStoragePayloadStub;
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Version;
import bisq.common.crypto.CryptoException;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static bisq.network.p2p.storage.TestState.*;

/**
 * Tests of the P2PDataStore behavior that expires old Entrys periodically.
 */
public class P2PDataStorageRemoveExpiredTest {
    private TestState testState;

    @Before
    public void setUp() {
        this.testState = new TestState();

        // Deep in the bowels of protobuf we grab the messageID from the version module. This is required to hash the
        // full MailboxStoragePayload so make sure it is initialized.
        Version.setBaseCryptoNetworkId(1);
    }

    // TESTCASE: Correctly skips entries that are not expirable
    @Test
    public void removeExpiredEntries_SkipsNonExpirableEntries() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload protectedStoragePayload = new ProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null, true));

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);
        this.testState.mockedStorage.removeExpiredEntries();

        this.testState.verifyProtectedStorageRemove(beforeState, protectedStorageEntry, false, false, false, false);
    }

    // TESTCASE: Correctly skips non-persistable entries that are not expired
    @Test
    public void removeExpiredEntries_SkipNonExpiredExpirableEntries() throws CryptoException, NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null, true));

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);
        this.testState.mockedStorage.removeExpiredEntries();

        this.testState.verifyProtectedStorageRemove(beforeState, protectedStorageEntry, false, false, false, false);
    }

    // TESTCASE: Correctly expires non-persistable entries that are expired
    @Test
    public void removeExpiredEntries_ExpiresExpiredExpirableEntries() throws CryptoException, NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayloadStub(ownerKeys.getPublic(), 0);
        ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null, true));

        // Increment the clock by an hour which will cause the Payloads to be outside the TTL range
        this.testState.incrementClock();

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);
        this.testState.mockedStorage.removeExpiredEntries();

        this.testState.verifyProtectedStorageRemove(beforeState, protectedStorageEntry, true, false, false, false);
    }

    // TESTCASE: Correctly skips persistable entries that are not expired
    @Test
    public void removeExpiredEntries_SkipNonExpiredPersistableExpirableEntries() throws CryptoException, NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload protectedStoragePayload = new PersistableExpirableProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null, true));

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);
        this.testState.mockedStorage.removeExpiredEntries();

        this.testState.verifyProtectedStorageRemove(beforeState, protectedStorageEntry, false, false, false, false);
    }

    // TESTCASE: Correctly expires persistable entries that are expired
    @Test
    public void removeExpiredEntries_ExpiresExpiredPersistableExpirableEntries() throws CryptoException, NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload protectedStoragePayload = new PersistableExpirableProtectedStoragePayloadStub(ownerKeys.getPublic(), 0);
        ProtectedStorageEntry protectedStorageEntry = testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        Assert.assertTrue(testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null, true));

        // Increment the clock by an hour which will cause the Payloads to be outside the TTL range
        this.testState.incrementClock();

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);
        this.testState.mockedStorage.removeExpiredEntries();

        this.testState.verifyProtectedStorageRemove(beforeState, protectedStorageEntry, true, false, false, false);
    }

    // TESTCASE: Ensure we try to purge old entries sequence number map when size exceeds the maximum size
    // and that entries less than PURGE_AGE_DAYS remain
    @Test
    public void removeExpiredEntries_PurgeSeqNrMap() throws CryptoException, NoSuchAlgorithmException {
        final int initialClockIncrement = 5;

        // Add 4 entries to our sequence number map that will be purged
        KeyPair purgedOwnerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload purgedProtectedStoragePayload = new PersistableExpirableProtectedStoragePayloadStub(purgedOwnerKeys.getPublic(), 0);
        ProtectedStorageEntry purgedProtectedStorageEntry = testState.mockedStorage.getProtectedStorageEntry(purgedProtectedStoragePayload, purgedOwnerKeys);

        Assert.assertTrue(testState.mockedStorage.addProtectedStorageEntry(purgedProtectedStorageEntry, TestState.getTestNodeAddress(), null, true));

        for (int i = 0; i < 4; ++i) {
            KeyPair ownerKeys = TestUtils.generateKeyPair();
            ProtectedStoragePayload protectedStoragePayload = new PersistableExpirableProtectedStoragePayloadStub(ownerKeys.getPublic(), 0);
            ProtectedStorageEntry tmpEntry = testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            Assert.assertTrue(testState.mockedStorage.addProtectedStorageEntry(tmpEntry, TestState.getTestNodeAddress(), null, true));
        }

        // Increment the time by 5 days which is less than the purge requirement. This will allow the map to have
        // some values that will be purged and others that will stay.
        this.testState.clockFake.increment(TimeUnit.DAYS.toMillis(initialClockIncrement));

        // Add a final entry that will not be purged
        KeyPair keepOwnerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload keepProtectedStoragePayload = new PersistableExpirableProtectedStoragePayloadStub(keepOwnerKeys.getPublic(), 0);
        ProtectedStorageEntry keepProtectedStorageEntry = testState.mockedStorage.getProtectedStorageEntry(keepProtectedStoragePayload, keepOwnerKeys);

        Assert.assertTrue(testState.mockedStorage.addProtectedStorageEntry(keepProtectedStorageEntry, TestState.getTestNodeAddress(), null, true));

        // P2PDataStorage::PURGE_AGE_DAYS == 10 days
        // Advance time past it so they will be valid purge targets
        this.testState.clockFake.increment(TimeUnit.DAYS.toMillis(P2PDataStorage.PURGE_AGE_DAYS + 1 - initialClockIncrement));

        // The first entry (11 days old) should be purged
        SavedTestState beforeState = this.testState.saveTestState(purgedProtectedStorageEntry);
        this.testState.mockedStorage.removeExpiredEntries();
        this.testState.verifyProtectedStorageRemove(beforeState, purgedProtectedStorageEntry, true, false, false, false);

        // Which means that an addition of a purged entry should succeed.
        beforeState = this.testState.saveTestState(purgedProtectedStorageEntry);
        Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(purgedProtectedStorageEntry, TestState.getTestNodeAddress(), null, false));
        this.testState.verifyProtectedStorageAdd(beforeState, purgedProtectedStorageEntry, true, false);

        // The second entry (5 days old) should still exist which means trying to add it again should fail.
        beforeState = this.testState.saveTestState(keepProtectedStorageEntry);
        Assert.assertFalse(this.testState.mockedStorage.addProtectedStorageEntry(keepProtectedStorageEntry, TestState.getTestNodeAddress(), null, false));
        this.testState.verifyProtectedStorageAdd(beforeState, keepProtectedStorageEntry, false, false);
    }
}
