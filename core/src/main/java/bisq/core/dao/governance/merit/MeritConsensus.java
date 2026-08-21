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

import bisq.core.dao.DaoHardFork;
import bisq.core.dao.governance.voteresult.VoteResultException;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.DaoArithmetics;
import bisq.core.dao.state.model.governance.MeritList;

import bisq.common.crypto.Encryption;

import javax.crypto.SecretKey;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

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

    /**
     * Returns the merit stake of every given blind vote, keyed by blind vote transaction id.
     * <p>
     * From the activation of the cycle wide uniqueness rule the merit of a blind vote depends on the other blind votes
     * of the same cycle, so the merit lists of the whole cycle have to be evaluated together. Before that height each
     * blind vote keeps being evaluated on its own.
     */
    public static Map<String, Long> getMeritStakeByBlindVoteTxId(Map<String, MeritList> untrustedMeritListByBlindVoteTxId,
                                                                 DaoStateService daoStateService,
                                                                 int evaluationBlockHeight) {
        if (isCycleWideMeritUniquenessActivated(evaluationBlockHeight)) {
            return MeritConsensusV3.getMeritStakeByBlindVoteTxId(untrustedMeritListByBlindVoteTxId, daoStateService);
        }

        Map<String, Long> meritStakeByBlindVoteTxId = new HashMap<>();
        // Sorted, so that a fault which aborts the vote result calculation is raised at the same blind vote on every
        // node.
        new TreeMap<>(untrustedMeritListByBlindVoteTxId).forEach((blindVoteTxId, untrustedMeritList) ->
                meritStakeByBlindVoteTxId.put(blindVoteTxId,
                        getMeritStake(blindVoteTxId, untrustedMeritList, daoStateService, evaluationBlockHeight)));
        return meritStakeByBlindVoteTxId;
    }

    /**
     * An issuance may back merit at most once per cycle. This is a DAO consensus change and activates with hard fork 3,
     * because the merit consensus V2 activation height has already passed and cycles have been evaluated under its
     * rules, which must keep their result.
     */
    public static boolean isCycleWideMeritUniquenessActivated(int blockHeight) {
        return DaoHardFork.isHardFork3Activated(blockHeight);
    }

    public static long getCurrentlyAvailableMerit(MeritList meritList, int currentChainHeight) {
        if (isMeritConsensusV2Activated(currentChainHeight)) {
            return MeritConsensusV2.getCurrentlyAvailableMerit(meritList, currentChainHeight);
        } else {
            return MeritConsensusLegacy.getCurrentlyAvailableMerit(meritList, currentChainHeight);
        }
    }
}
