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

package bisq.core.dao.governance.merit;

import bisq.core.dao.governance.voteresult.VoteResultException;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.DaoArithmetics;
import bisq.core.dao.state.model.governance.MeritList;

import bisq.common.crypto.Encryption;

import javax.crypto.SecretKey;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MeritConsensus {
    public static final int MERIT_CONSENSUS_V2_ACTIVATION_HEIGHT = 954_200;

    public static MeritList decryptMeritList(byte[] encryptedMeritList, SecretKey secretKey)
            throws VoteResultException.DecryptionException {
        try {
            byte[] decrypted = Encryption.decrypt(encryptedMeritList, secretKey);
            return MeritList.getMeritListFromBytes(decrypted);
        } catch (Throwable t) {
            throw new VoteResultException.DecryptionException(t);
        }
    }

    public static long getMeritStake(String blindVoteTxId, MeritList meritList, DaoStateService daoStateService) {
        return getMeritStake(blindVoteTxId, meritList, daoStateService, daoStateService.getChainHeight());
    }

    public static long getMeritStake(String blindVoteTxId,
                                     MeritList meritList,
                                     DaoStateService daoStateService,
                                     int evaluationBlockHeight) {
        if (isMeritConsensusV2Activated(evaluationBlockHeight)) {
            return MeritConsensusV2.getMeritStake(blindVoteTxId, meritList, daoStateService);
        } else {
            return MeritConsensusLegacy.getMeritStake(blindVoteTxId, meritList, daoStateService);
        }
    }

    public static boolean isMeritConsensusV2Activated(int blockHeight) {
        return blockHeight >= MERIT_CONSENSUS_V2_ACTIVATION_HEIGHT;
    }

    public static long getCurrentlyAvailableMerit(MeritList meritList, int currentChainHeight) {
        if (isMeritConsensusV2Activated(currentChainHeight)) {
            return MeritConsensusV2.getCurrentlyAvailableMerit(meritList, currentChainHeight);
        } else {
            return MeritConsensusLegacy.getCurrentlyAvailableMerit(meritList, currentChainHeight);
        }
    }
}
