package bisq.core.dao.governance.ballot;

import bisq.core.dao.governance.ballot.BallotListService.BallotListChangeListener;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.ProposalValidatorProvider;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;

import bisq.common.persistence.PersistenceManager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BallotListServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    public void testAddListenersWhenNewPayloadAdded() {
        // given
        ObservableList<ProposalPayload> payloads = FXCollections.observableArrayList();

        ProposalService proposalService = mock(ProposalService.class);
        when(proposalService.getProposalPayloads()).thenReturn(payloads);

        BallotListService service = new BallotListService(proposalService, mock(PeriodService.class),
                mock(ProposalValidatorProvider.class), mock(PersistenceManager.class));

        BallotListChangeListener listener = mock(BallotListChangeListener.class);
        service.addListener(listener);

        service.addListeners();

        // when
        payloads.add(mock(ProposalPayload.class, RETURNS_DEEP_STUBS));

        // then
        verify(listener).onListChanged(any());
    }
}
