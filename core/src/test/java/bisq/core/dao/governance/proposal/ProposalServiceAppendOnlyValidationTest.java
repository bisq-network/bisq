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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.governance.proposal;

import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalStorageService;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalStorageService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.GenericProposal;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProposalServiceAppendOnlyValidationTest {
    private static final String TX_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private P2PService p2PService;
    @Mock
    private ProposalStorageService proposalStorageService;
    @Mock
    private DaoStateService daoStateService;
    @Mock
    private ProposalValidatorProvider validatorProvider;
    @Mock
    private ProposalValidator validator;

    private final Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> proposalStore = new HashMap<>();
    private ProposalService proposalService;

    @BeforeEach
    public void setUp() {
        when(p2PService.getDataMap()).thenReturn(Collections.emptyMap());
        when(proposalStorageService.getMap()).thenReturn(proposalStore);
        when(validatorProvider.getValidator(any(Proposal.class))).thenReturn(validator);

        proposalService = new ProposalService(p2PService,
                mock(PeriodService.class),
                proposalStorageService,
                mock(TempProposalStorageService.class),
                mock(AppendOnlyDataStoreService.class),
                mock(ProtectedDataStoreService.class),
                daoStateService,
                validatorProvider);
    }

    @Test
    public void startupAndLivePathsBothRejectInvalidCommonFields() {
        ProposalPayload payload = createPayload();
        proposalStore.put(new P2PDataStorage.ByteArray(payload.getHash()), payload);
        when(validator.areCommonDataFieldsValid(payload.getProposal())).thenReturn(false);
        when(validator.areDataFieldsValid(payload.getProposal())).thenReturn(false);
        when(validator.isProposalDataFieldValidationActivated(payload.getProposal())).thenReturn(true);

        when(daoStateService.isParseBlockChainComplete()).thenReturn(false);
        proposalService.start();
        assertTrue(proposalService.getProposalPayloads().isEmpty());

        when(daoStateService.isParseBlockChainComplete()).thenReturn(true);
        proposalService.onAdded(payload);
        assertTrue(proposalService.getProposalPayloads().isEmpty());

        verify(validator).areCommonDataFieldsValid(payload.getProposal());
        verify(validator).areDataFieldsValid(payload.getProposal());
    }

    @Test
    public void parserDependentFieldsAreRevalidatedAfterStartupParsing() {
        ProposalPayload payload = createPayload();
        proposalStore.put(new P2PDataStorage.ByteArray(payload.getHash()), payload);
        when(validator.areCommonDataFieldsValid(payload.getProposal())).thenReturn(true);
        when(validator.areDataFieldsValid(payload.getProposal())).thenReturn(false);
        when(validator.isProposalDataFieldValidationActivated(payload.getProposal())).thenReturn(true);
        when(daoStateService.isParseBlockChainComplete()).thenReturn(false);

        proposalService.start();
        assertEquals(Collections.singletonList(payload), proposalService.getProposalPayloads());
        verify(validator, never()).areDataFieldsValid(payload.getProposal());

        when(daoStateService.isParseBlockChainComplete()).thenReturn(true);
        proposalService.onParseBlockChainComplete();

        assertTrue(proposalService.getProposalPayloads().isEmpty());
        verify(validator).areDataFieldsValid(payload.getProposal());
    }

    @Test
    public void preActivationPayloadKeepsCommonFieldAdmissionAfterParsing() {
        ProposalPayload payload = createPayload();
        when(daoStateService.isParseBlockChainComplete()).thenReturn(true);
        when(validator.isProposalDataFieldValidationActivated(payload.getProposal())).thenReturn(false);
        when(validator.areCommonDataFieldsValid(payload.getProposal())).thenReturn(true);

        proposalService.start();
        proposalService.onAdded(payload);

        assertEquals(Collections.singletonList(payload), proposalService.getProposalPayloads());
        verify(validator, never()).areDataFieldsValid(payload.getProposal());
    }

    private ProposalPayload createPayload() {
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(TX_ID);
        return new ProposalPayload(proposal);
    }
}
