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

package bisq.core.notifications;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class MobileNotificationValidator {
    @Inject
    public MobileNotificationValidator() {
    }

    public boolean isValid(String keyAndToken) {
        if (keyAndToken == null)
            return false;

        String[] tokens = keyAndToken.split(MobileModel.PHONE_SEPARATOR_ESCAPED);
        if (tokens.length != 4) {
            log.error("invalid pairing ID format: not 4 sections separated by " + MobileModel.PHONE_SEPARATOR_WRITING);
            return false;
        }
        String magic = tokens[0];
        String key = tokens[2];
        String phoneId = tokens[3];

        if (key.length() != 32) {
            log.error("invalid pairing ID format: key not 32 bytes");
            return false;
        }

        if (magic.equals(MobileModel.OS.IOS.getMagicString()) ||
                magic.equals(MobileModel.OS.IOS_DEV.getMagicString())) {
            if (phoneId.length() != 64) {
                log.error("invalid Bisq MobileModel ID format: iOS token not 64 bytes");
                return false;
            }
        } else if (magic.equals(MobileModel.OS.ANDROID.getMagicString())) {
            if (phoneId.length() < 32) {
                log.error("invalid Bisq MobileModel ID format: Android token too short (<32 bytes)");
                return false;
            }
        } else {
            log.error("invalid Bisq MobileModel ID format");
            return false;
        }

        return true;
    }
}
