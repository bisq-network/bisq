/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.p2p;

import io.bitsquare.crypto.EncryptionService;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
    Stores a message in encrypted form, so it never leaves the client in plain text.
 */
public class EncryptedMailboxMessage implements MailboxMessage, Serializable {
    private static final long serialVersionUID = -3111178895546299769L;
    private static final Logger log = LoggerFactory.getLogger(EncryptedMailboxMessage.class);

    private EncryptionService.Tuple tuple;

    public EncryptedMailboxMessage(EncryptionService.Tuple tuple) {
        this.tuple = tuple;
    }

    public EncryptionService.Tuple getTuple() {
        return tuple;
    }
}
