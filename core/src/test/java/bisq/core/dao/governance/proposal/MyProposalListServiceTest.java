package bisq.core.dao.governance.proposal;

import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.PubKeyRing;
import bisq.common.persistence.PersistenceManager;

import javafx.beans.property.SimpleIntegerProperty;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MyProposalListServiceTest {
    @Test
    public void canInstantiate(@Mock PersistenceManager<MyProposalList> persistenceManager) {
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getNumConnectedPeers()).thenReturn(new SimpleIntegerProperty(0));

        new MyProposalListService(p2PService, mock(DaoStateService.class), mock(PeriodService.class),
                mock(WalletsManager.class), persistenceManager, mock(PubKeyRing.class)
        );
    }
}
