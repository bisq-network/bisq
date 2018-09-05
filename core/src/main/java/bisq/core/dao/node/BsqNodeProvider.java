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

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.node.full.FullNode;
import bisq.core.dao.node.lite.LiteNode;

import com.google.inject.Inject;

import javax.inject.Named;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Returns a FullNode or LiteNode based on the DaoOptionKeys.FULL_DAO_NODE option.
 */
@Slf4j
public class BsqNodeProvider {
    @Getter
    private final BsqNode bsqNode;

    @Inject
    public BsqNodeProvider(LiteNode bsqLiteNode,
                           FullNode bsqFullNode,
                           @Named(DaoOptionKeys.FULL_DAO_NODE) boolean fullDaoNode) {
        bsqNode = fullDaoNode ? bsqFullNode : bsqLiteNode;
    }
}
