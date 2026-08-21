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

package bisq.core.api.model;

import bisq.common.Payload;

public class AddressBalanceInfo implements Payload {

    private final String address;
    private final long balance;             // address' balance in satoshis
    private final long numConfirmations;    // # confirmations for address' most recent tx
    private final boolean isAddressUnused;

    public AddressBalanceInfo(String address,
                              long balance,
                              long numConfirmations,
                              boolean isAddressUnused) {
        this.address = address;
        this.balance = balance;
        this.numConfirmations = numConfirmations;
        this.isAddressUnused = isAddressUnused;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.AddressBalanceInfo toProtoMessage() {
        return bisq.proto.grpc.AddressBalanceInfo.newBuilder()
                .setAddress(address)
                .setBalance(balance)
                .setNumConfirmations(numConfirmations)
                .setIsAddressUnused(isAddressUnused)
                .build();
    }

    public static AddressBalanceInfo fromProto(bisq.proto.grpc.AddressBalanceInfo proto) {
        return new AddressBalanceInfo(proto.getAddress(),
                proto.getBalance(),
                proto.getNumConfirmations(),
                proto.getIsAddressUnused());
    }

    @Override
    public String toString() {
        return "AddressBalanceInfo{" +
                "address='" + address + '\'' +
                ", balance=" + balance +
                ", numConfirmations=" + numConfirmations +
                ", isAddressUnused=" + isAddressUnused +
                '}';
    }
}
