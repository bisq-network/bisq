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

package bisq.core.dao.governance.voteresult;

import bisq.core.dao.governance.ballot.Ballot;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Value;

public class VoteResultException extends Exception {

    VoteResultException(Throwable cause) {
        super(cause);
    }

    private VoteResultException(String message) {
        super(message);
    }

    private VoteResultException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String toString() {
        return "VoteResultException{" +
                "\n} " + super.toString();
    }

    @EqualsAndHashCode(callSuper = true)
    public static class ConsensusException extends VoteResultException {

        ConsensusException(String message) {
            super(message);
        }

        @Override
        public String toString() {
            return "ConsensusException{" +
                    "\n} " + super.toString();
        }
    }

    @EqualsAndHashCode(callSuper = true)
    public static class ValidationException extends VoteResultException {

        ValidationException(Throwable cause) {
            super("Validation of vote result failed.", cause);
        }

        @Override
        public String toString() {
            return "VoteResultException{" +
                    "\n     cause=" + getCause() +
                    "\n} " + super.toString();
        }
    }

    @EqualsAndHashCode(callSuper = true)
    public static abstract class MissingDataException extends VoteResultException {
        private MissingDataException(String message) {
            super(message);
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @Value
    public static class MissingBlindVoteDataException extends MissingDataException {
        private String blindVoteTxId;

        MissingBlindVoteDataException(String blindVoteTxId) {
            super("Blind vote tx ID " + blindVoteTxId + " is missing");
            this.blindVoteTxId = blindVoteTxId;
        }

        @Override
        public String toString() {
            return "MissingBlindVoteDataException{" +
                    "\n     blindVoteTxId='" + blindVoteTxId + '\'' +
                    "\n} " + super.toString();
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @Value
    public static class MissingBallotException extends MissingDataException {
        private List<Ballot> existingBallots;
        private List<String> proposalTxIdsOfMissingBallots;

        MissingBallotException(List<Ballot> existingBallots, List<String> proposalTxIdsOfMissingBallots) {
            super("Missing ballots. proposalTxIdsOfMissingBallots=" + proposalTxIdsOfMissingBallots);
            this.existingBallots = existingBallots;
            this.proposalTxIdsOfMissingBallots = proposalTxIdsOfMissingBallots;
        }
    }


    @EqualsAndHashCode(callSuper = true)
    @Value
    public static class DecryptionException extends VoteResultException {
        public DecryptionException(Throwable cause) {
            super(cause);
        }

        @Override
        public String toString() {
            return "DecryptionException{" +
                    "\n} " + super.toString();
        }
    }
}
