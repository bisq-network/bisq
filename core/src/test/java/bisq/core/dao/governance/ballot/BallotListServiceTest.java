package bisq.core.dao.governance.ballot;

import bisq.core.dao.governance.ballot.BallotListService.BallotListChangeListener;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.governance.proposal.ProposalValidatorProvider;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BallotList;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.common.persistence.PersistenceManager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Collections;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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

    @Test
    @SuppressWarnings("unchecked")
    public void persistedInvalidBallotIsExcludedByConsensusValidation() {
        Proposal proposal = mock(Proposal.class);
        Ballot ballot = new Ballot(proposal);
        ProposalValidator validator = mock(ProposalValidator.class);
        ProposalValidatorProvider validatorProvider = mock(ProposalValidatorProvider.class);
        when(validatorProvider.getValidator(proposal)).thenReturn(validator);
        when(validator.isValidForConsensus(proposal)).thenReturn(false);

        PersistenceManager<BallotList> persistenceManager = mock(PersistenceManager.class);
        doAnswer(invocation -> {
            Consumer<BallotList> resultHandler = invocation.getArgument(0);
            resultHandler.accept(new BallotList(Collections.singletonList(ballot)));
            return null;
        }).when(persistenceManager).readPersisted(any(), any());

        BallotListService service = new BallotListService(mock(ProposalService.class),
                mock(PeriodService.class),
                validatorProvider,
                persistenceManager);
        service.readPersisted(() -> {
        });

        assertTrue(service.getValidBallotsOfCycle().isEmpty());
        verify(validator).isValidForConsensus(proposal);
    }
}
