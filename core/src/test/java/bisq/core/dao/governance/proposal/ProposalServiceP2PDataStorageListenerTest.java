/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.governance.proposal;

import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalStorageService;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalPayload;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalStorageService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;

import javafx.collections.ListChangeListener;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


/**
 * Tests of the P2PDataStorage::onRemoved callback behavior to ensure that the proper number of signal events occur.
 */
public class ProposalServiceP2PDataStorageListenerTest {
    private ProposalService proposalService;

    @Mock
    private PeriodService periodService;

    @Mock
    private DaoStateService daoStateService;

    @Mock
    private ListChangeListener<Proposal> tempProposalListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        this.proposalService = new ProposalService(
                mock(P2PService.class),
                this.periodService,
                mock(ProposalStorageService.class),
                mock(TempProposalStorageService.class),
                mock(AppendOnlyDataStoreService.class),
                mock(ProtectedDataStoreService.class),
                this.daoStateService,
                mock(ProposalValidatorProvider.class),
                true);

        // Create a state so that all added/removed Proposals will actually update the tempProposals list.
        when(this.periodService.isInPhase(anyInt(), any(DaoPhase.Phase.class))).thenReturn(true);
        when(this.daoStateService.isParseBlockChainComplete()).thenReturn(false);
    }

    private static ProtectedStorageEntry buildProtectedStorageEntry() {
        ProtectedStorageEntry protectedStorageEntry = mock(ProtectedStorageEntry.class);
        TempProposalPayload tempProposalPayload = mock(TempProposalPayload.class);
        Proposal tempProposal = mock(Proposal.class);
        when(protectedStorageEntry.getProtectedStoragePayload()).thenReturn(tempProposalPayload);
        when(tempProposalPayload.getProposal()).thenReturn(tempProposal);

        return protectedStorageEntry;
    }

    // TESTCASE: If an onRemoved callback is called which does not remove anything the tempProposals listeners
    // are not signaled.
    @Test
    public void onRemoved_noSignalIfNoChange() {
        this.proposalService.onRemoved(Collections.singletonList(mock(ProtectedStorageEntry.class)));

        verify(this.tempProposalListener, never()).onChanged(any());
    }

    // TESTCASE: If an onRemoved callback is called with 1 element AND it creates a remove of 1 element, the tempProposal
    // listeners are signaled once.
    @Test
    public void onRemoved_signalOnceOnOneChange() {
        ProtectedStorageEntry one = buildProtectedStorageEntry();
        this.proposalService.onAdded(Collections.singletonList(one));
        this.proposalService.getTempProposals().addListener(this.tempProposalListener);

        this.proposalService.onRemoved(Collections.singletonList(one));

        verify(this.tempProposalListener).onChanged(any());
    }

    // TESTCASE: If an onRemoved callback is called with 2 elements AND it creates a remove of 2 elements, the
    // tempProposal listeners are signaled once.
    @Test
    public void onRemoved_signalOnceOnMultipleChanges() {
        ProtectedStorageEntry one = buildProtectedStorageEntry();
        ProtectedStorageEntry two = buildProtectedStorageEntry();
        this.proposalService.onAdded(Arrays.asList(one, two));
        this.proposalService.getTempProposals().addListener(this.tempProposalListener);

        this.proposalService.onRemoved(Arrays.asList(one, two));

        verify(this.tempProposalListener).onChanged(any());
    }
}
