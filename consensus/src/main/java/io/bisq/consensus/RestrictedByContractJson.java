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

package io.bisq.consensus;

/**
 * Market interface for classes which are used in the trade contract.
 * Any change of the class fields would breaking backward compatibility.
 * If a field needs to get added it needs to be annotated with @JsonExclude (thus excluded from the contract JSON).
 * Better to use the excludeFromJsonDataMap (annotated with @JsonExclude; used in PaymentAccountPayload) to
 * add a key/value pair.
 */
public interface RestrictedByContractJson {
}
