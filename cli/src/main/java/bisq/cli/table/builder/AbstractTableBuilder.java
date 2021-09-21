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

package bisq.cli.table.builder;

import bisq.proto.grpc.OfferInfo;

import java.util.List;
import java.util.function.Predicate;

/**
 * Abstract superclass for TableBuilder implementations.
 */
abstract class AbstractTableBuilder {

    protected final TableType tableType;
    protected final List<?> protos;

    public AbstractTableBuilder(TableType tableType, List<?> protos) {
        this.tableType = tableType;
        this.protos = protos;
        if (protos.isEmpty())
            throw new IllegalArgumentException("proto list is empty");
    }

    protected final Predicate<OfferInfo> isFiatOffer = (o) -> o.getBaseCurrencyCode().equals("BTC");
}
