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

package bisq.core.dao.burningman.model;

import java.util.Date;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public final class CompensationModel {
    public static CompensationModel fromCompensationRequest(String address,
                                                            boolean isCustomAddress,
                                                            long amount,
                                                            long decayedAmount,
                                                            int height,
                                                            String txId,
                                                            long date,
                                                            int cycleIndex) {
        return new CompensationModel(address,
                isCustomAddress,
                amount,
                decayedAmount,
                height,
                txId,
                Optional.empty(),
                date,
                cycleIndex);
    }

    public static CompensationModel fromGenesisOutput(String address,
                                                      long amount,
                                                      long decayedAmount,
                                                      int height,
                                                      String txId,
                                                      int outputIndex,
                                                      long date) {
        return new CompensationModel(address,
                false,
                amount,
                decayedAmount,
                height,
                txId,
                Optional.of(outputIndex),
                date,
                0);
    }


    private final String address;
    private final boolean isCustomAddress;
    private final long amount;
    private final long decayedAmount;
    private final int height;
    private final String txId;
    private final Optional<Integer> outputIndex; // Only set for genesis tx outputs as needed for sorting
    private final long date;
    private final int cycleIndex;

    private CompensationModel(String address,
                              boolean isCustomAddress,
                              long amount,
                              long decayedAmount,
                              int height,
                              String txId,
                              Optional<Integer> outputIndex,
                              long date,
                              int cycleIndex) {
        this.address = address;
        this.isCustomAddress = isCustomAddress;
        this.amount = amount;
        this.decayedAmount = decayedAmount;
        this.height = height;
        this.txId = txId;
        this.outputIndex = outputIndex;
        this.date = date;
        this.cycleIndex = cycleIndex;
    }

    @Override
    public String toString() {
        return "\n          CompensationModel{" +
                "\r\n                address='" + address + '\'' +
                "\r\n                isCustomAddress='" + isCustomAddress + '\'' +
                ",\r\n               amount=" + amount +
                ",\r\n               decayedAmount=" + decayedAmount +
                ",\r\n               height=" + height +
                ",\r\n               txId='" + txId + '\'' +
                ",\r\n               outputIndex=" + outputIndex +
                ",\r\n               date=" + new Date(date) +
                ",\r\n               cycleIndex=" + cycleIndex +
                "\r\n          }";
    }
}
