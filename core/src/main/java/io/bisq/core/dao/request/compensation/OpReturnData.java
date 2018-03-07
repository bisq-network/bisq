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

package io.bisq.core.dao.request.compensation;

import io.bisq.common.app.Version;
import io.bisq.common.crypto.Hash;
import io.bisq.core.dao.OpReturnTypes;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
public class OpReturnData {

    public static byte[] getBytes(String input) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] dataAndSigAsBytes = input.getBytes();
            outputStream.write(OpReturnTypes.COMPENSATION_REQUEST);
            outputStream.write(Version.COMPENSATION_REQUEST_VERSION);
            outputStream.write(Hash.getSha256Ripemd160hash(dataAndSigAsBytes));
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Not expected to happen ever
            e.printStackTrace();
            log.error(e.toString());
            return new byte[0];
        }
    }
}
