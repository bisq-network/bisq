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

package bisq.core.dao.node;

import bisq.network.p2p.network.Connection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Keeps the blocks of a response below the maximum message size, above which the requester refuses the message. A
 * requester asks for the blocks after the last one it received, so a response may hold fewer blocks than requested.
 */
public final class ResponseSizeLimit {
    // Half of the maximum message size, which leaves room for the rest of the message
    public static final int MAX_BLOCKS_SIZE = (int) (Connection.getMaxPermittedMessageSize() * 0.5);

    private ResponseSizeLimit() {
    }

    /**
     * Returns the leading elements whose serialized sizes add up to at most maxSize, and at least the first element,
     * so that a response is never empty because of one large element.
     */
    public static <T> List<T> leadingElementsWithin(Iterable<T> elements, ToIntFunction<T> serializedSize, int maxSize) {
        List<T> result = new ArrayList<>();
        long size = 0;
        for (T element : elements) {
            size += serializedSize.applyAsInt(element);
            if (size > maxSize && !result.isEmpty()) {
                break;
            }
            result.add(element);
        }
        return result;
    }
}
