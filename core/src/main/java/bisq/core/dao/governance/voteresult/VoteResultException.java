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

import bisq.core.dao.governance.blindvote.storage.BlindVotePayload;

import lombok.Getter;

import javax.annotation.Nullable;

public class VoteResultException extends Exception {
    @Getter
    @Nullable
    private BlindVotePayload blindVotePayload;

    public VoteResultException(String message, Exception cause, BlindVotePayload blindVotePayload) {
        super(message, cause);
        this.blindVotePayload = blindVotePayload;
    }

    public VoteResultException(String message) {
        super(message);
    }

    public VoteResultException(Throwable cause) {
        super(cause);
    }

    @Override
    public String toString() {
        return "VoteResultException{" +
                "\n     blindVotePayload=" + blindVotePayload +
                "\n     cause=" + getCause() +
                "\n} " + super.toString();
    }
}
