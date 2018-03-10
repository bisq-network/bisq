/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.request.altcoin;

import io.bisq.core.dao.request.VoteRequest;

/**
 * Request for removing an altcoin. Altcoins are added if they fulfill the formal requirements but can be requested by
 * stakeholders to get removed for any reasons (e.g. majority of stakeholder consider it a scam coin).
 */

// TODO implement
public class RemoveAltcoinRequest extends VoteRequest {

    public RemoveAltcoinRequest() {
    }
}
