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

package bisq.price.util;

import bisq.price.PriceController;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStreamReader;

@RestController
class VersionController extends PriceController implements InfoContributor {

    private final String version;

    public VersionController(@Value("classpath:version.txt") Resource versionTxt) throws IOException {
        this.version = FileCopyUtils.copyToString(
            new InputStreamReader(
                versionTxt.getInputStream()
            )
        ).trim();
    }

    @GetMapping(path = "/getVersion")
    public String getVersion() {
        return version;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("version", version);
    }
}
