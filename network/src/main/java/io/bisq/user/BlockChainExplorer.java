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

package io.bisq.user;

import io.bisq.app.Version;
import io.bisq.common.persistance.Persistable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlockChainExplorer implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(BlockChainExplorer.class);

    public final String name;
    public final String txUrl;
    public final String addressUrl;

    public BlockChainExplorer(String name, String txUrl, String addressUrl) {
        this.name = name;
        this.txUrl = txUrl;
        this.addressUrl = addressUrl;
    }
}
