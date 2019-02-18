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

package bisq.core.dao.node.parser.exceptions;

import bisq.core.dao.node.full.RawBlock;

import lombok.Getter;

@Getter
public class BlockHeightNotConnectingException extends Exception {

    private RawBlock rawBlock;

    public BlockHeightNotConnectingException(RawBlock rawBlock) {
        this.rawBlock = rawBlock;
    }

    @Override
    public String toString() {
        return "BlockHeightNotConnectingException{" +
                "\n     rawBlock.getHash=" + rawBlock.getHash() +
                "\n     rawBlock.getHeight=" + rawBlock.getHeight() +
                "\n     rawBlock.getPreviousBlockHash=" + rawBlock.getPreviousBlockHash() +
                "\n} " + super.toString();
    }
}
