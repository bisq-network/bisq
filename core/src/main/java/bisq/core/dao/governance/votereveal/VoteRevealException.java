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

package bisq.core.dao.governance.votereveal;

import bisq.core.dao.governance.myvote.MyVote;

import org.bitcoinj.core.Transaction;

import lombok.Getter;

import javax.annotation.Nullable;

@SuppressWarnings("SameParameterValue")
public class VoteRevealException extends Exception {
    @Getter
    @Nullable
    private Transaction voteRevealTx;
    @Getter
    @Nullable
    private String blindVoteTxId;
    @Getter
    @Nullable
    private MyVote myVote;

    VoteRevealException(String message, Throwable cause, @SuppressWarnings("NullableProblems") String blindVoteTxId) {
        super(message, cause);
        this.blindVoteTxId = blindVoteTxId;
    }

    VoteRevealException(String message, @SuppressWarnings("NullableProblems") MyVote myVote) {
        super(message);
        this.myVote = myVote;
    }

    VoteRevealException(String message, Throwable cause, @SuppressWarnings("NullableProblems") Transaction voteRevealTx) {
        super(message, cause);
        this.voteRevealTx = voteRevealTx;
    }


    @Override
    public String toString() {
        return "VoteRevealException{" +
                "\n     voteRevealTx=" + voteRevealTx +
                ",\n     blindVoteTxId='" + blindVoteTxId + '\'' +
                ",\n     myVote=" + myVote +
                "\n} " + super.toString();
    }
}
