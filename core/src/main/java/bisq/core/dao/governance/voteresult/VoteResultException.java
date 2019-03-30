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

import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.Cycle;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
public class VoteResultException extends Exception {
    @Getter
    private final int heightOfFirstBlockInCycle;

    VoteResultException(Cycle cycle, Throwable cause) {
        super(cause);
        this.heightOfFirstBlockInCycle = cycle.getHeightOfFirstBlock();
    }

    @Override
    public String toString() {
        return "VoteResultException{" +
                "\n     heightOfFirstBlockInCycle=" + heightOfFirstBlockInCycle +
                "\n} " + super.toString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static sub classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    @EqualsAndHashCode(callSuper = true)
    public static class ConsensusException extends Exception {

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
    public static class ValidationException extends Exception {

        ValidationException(Throwable cause) {
            super("Validation of vote result failed.", cause);
        }

        public ValidationException(String message) {
            super(message);
        }

        @Override
        public String toString() {
            return "VoteResultException{" +
                    "\n     cause=" + getCause() +
                    "\n} " + super.toString();
        }
    }

    @EqualsAndHashCode(callSuper = true)
    static abstract class MissingDataException extends Exception {
        private MissingDataException(String message) {
            super(message);
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
    public static class DecryptionException extends Exception {
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
