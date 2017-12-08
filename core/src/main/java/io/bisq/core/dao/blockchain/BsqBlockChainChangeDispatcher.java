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

package io.bisq.core.dao.blockchain;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Passing the BsqNode directly to the classes interested in onBsqBlockChainChanged events cause Guice dependency issues,
 * so we use that object to isolate that concern.
 */
@Slf4j
public class BsqBlockChainChangeDispatcher implements BsqBlockChainListener {
    private final List<BsqBlockChainListener> bsqBlockChainListeners = new ArrayList<>();

    public BsqBlockChainChangeDispatcher() {
    }

    @Override
    public void onBsqBlockChainChanged() {
        bsqBlockChainListeners.stream().forEach(BsqBlockChainListener::onBsqBlockChainChanged);
    }

    public void addBsqBlockChainListener(BsqBlockChainListener bsqBlockChainListener) {
        bsqBlockChainListeners.add(bsqBlockChainListener);
    }

    public void removeBsqBlockChainListener(BsqBlockChainListener bsqBlockChainListener) {
        bsqBlockChainListeners.remove(bsqBlockChainListener);
    }
}
