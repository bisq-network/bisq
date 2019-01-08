package bisq.core.dao.governance.ballot;

import bisq.common.storage.Storage;
import bisq.core.dao.governance.ballot.BallotListService.BallotListChangeListener;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PeriodService.class, ProposalPayload.class})
public class BallotListServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    public void testAddListenersWhenNewPayloadAdded() {
        // given
        ObservableList<ProposalPayload> payloads = FXCollections.observableArrayList();

        ProposalService proposalService = mock(ProposalService.class);
        when(proposalService.getProposalPayloads()).thenReturn(payloads);

        BallotListService service = new BallotListService(proposalService, mock(PeriodService.class),
                mock(ProposalValidator.class), mock(Storage.class));

        BallotListChangeListener listener = mock(BallotListChangeListener.class);
        service.addListener(listener);

        service.addListeners();

        // when
        payloads.add(mock(ProposalPayload.class, RETURNS_DEEP_STUBS));

        // then
        verify(listener).onListChanged(any());
    }
}
