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

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Data
@Slf4j
@Singleton
public class MobileModel {
    public static final String PHONE_SEPARATOR_ESCAPED = "\\|"; // see https://stackoverflow.com/questions/5675704/java-string-split-not-returning-the-right-values
    public static final String PHONE_SEPARATOR_WRITING = "|";

    public enum OS {
        UNDEFINED(""),
        IOS("iOS"),
        IOS_DEV("iOSDev"),
        ANDROID("android");

        @Getter
        private String magicString;

        OS(String magicString) {
            this.magicString = magicString;
        }
    }

    @Nullable
    private OS os;
    @Nullable
    private String descriptor;
    @Nullable
    private String key;
    @Nullable
    private String token;
    private boolean isContentAvailable = true;

    @Inject
    public MobileModel() {
    }

    public void reset() {
        os = null;
        key = null;
        token = null;
    }

    public void applyKeyAndToken(String keyAndToken) {
        log.info("applyKeyAndToken: keyAndToken={}", keyAndToken.substring(0, 20) + "...(truncated in log for privacy reasons)");
        String[] tokens = keyAndToken.split(PHONE_SEPARATOR_ESCAPED);
        String magic = tokens[0];
        descriptor = tokens[1];
        key = tokens[2];
        token = tokens[3];
        if (magic.equals(OS.IOS.getMagicString()))
            os = OS.IOS;
        else if (magic.equals(OS.IOS_DEV.getMagicString()))
            os = OS.IOS_DEV;
        else if (magic.equals(OS.ANDROID.getMagicString()))
            os = OS.ANDROID;

        isContentAvailable = parseDescriptor(descriptor);
    }

    @VisibleForTesting
    boolean parseDescriptor(String descriptor) {
        // phone descriptors
        /*
        iPod Touch 5
        iPod Touch 6
        iPhone 4
        iPhone 4s
        iPhone 5
        iPhone 5c
        iPhone 5s
        iPhone 6
        iPhone 6 Plus
        iPhone 6s
        iPhone 6s Plus
        iPhone 7
        iPhone 7 Plus
        iPhone SE
        iPhone 8
        iPhone 8 Plus
        iPhone X
        iPhone XS
        iPhone XS Max
        iPhone XR
        iPhone 11
        iPhone 11 Pro
        iPhone 11 Pro Max
        iPad 2
        iPad 3
        iPad 4
        iPad Air
        iPad Air 2
        iPad 5
        iPad 6
        iPad Mini
        iPad Mini 2
        iPad Mini 3
        iPad Mini 4
        iPad Pro 9.7 Inch
        iPad Pro 12.9 Inch
        iPad Pro 12.9 Inch 2. Generation
        iPad Pro 10.5 Inch
        */
        // iPhone 6 does not support isContentAvailable, iPhone 6s and 7 does.
        // We don't know about other versions, but let's assume all above iPhone 6 are ok.
        if (descriptor != null) {
            String[] descriptorTokens = descriptor.split(" ");
            if (descriptorTokens.length >= 1) {
                String model = descriptorTokens[0];
                if (model.equals("iPhone")) {
                    String versionString = descriptorTokens[1];
                    String[] validVersions = {"X", "XS", "XR"};
                    if (Arrays.asList(validVersions).contains(versionString)) {
                        return true;
                    }
                    String versionSuffix = "";
                    if (versionString.matches("\\d[^\\d]")) {
                        versionSuffix = versionString.substring(1);
                        versionString = versionString.substring(0, 1);
                    } else if (versionString.matches("\\d{2}[^\\d]")) {
                        versionSuffix = versionString.substring(2);
                        versionString = versionString.substring(0, 2);
                    }
                    try {
                        int version = Integer.parseInt(versionString);
                        return version > 6 || (version == 6 && versionSuffix.equalsIgnoreCase("s"));
                    } catch (Throwable ignore) {
                    }
                } else {
                    return (model.equals("iPad")) && descriptorTokens[1].equals("Pro");
                }
            }
        }
        return false;
    }
}
