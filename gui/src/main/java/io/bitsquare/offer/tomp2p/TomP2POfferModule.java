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

package io.bitsquare.offer.tomp2p;

import io.bitsquare.offer.OfferModule;
import io.bitsquare.offer.OfferRepository;

import org.springframework.core.env.Environment;

public class TomP2POfferModule extends OfferModule {

    public TomP2POfferModule(Environment env) {
        super(env);
    }

    @Override
    public Class<? extends OfferRepository> offerRepository() {
        return TomP2POfferRepository.class;
    }
}
