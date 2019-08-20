package bisq.network.p2p.storage;

import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.ResourceDataStoreService;

import bisq.common.storage.Storage;

import java.time.Clock;

import org.junit.Test;

import static org.mockito.Mockito.mock;

public class P2PDataStorageTest {
    @Test
    public void canStart1Instance() {
        P2PDataStorage storage = new P2PDataStorage(mock(NetworkNode.class),
                mock(Broadcaster.class),
                mock(AppendOnlyDataStoreService.class),
                mock(ProtectedDataStoreService.class), mock(ResourceDataStoreService.class),
                mock(Storage.class), Clock.systemUTC());

    }

}
