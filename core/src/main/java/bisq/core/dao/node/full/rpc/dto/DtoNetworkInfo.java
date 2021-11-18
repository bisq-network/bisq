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

package bisq.core.dao.node.full.rpc.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"version", "subversion", "protocolversion", "localservices", "localservicesnames", "localrelay",
        "timeoffset", "networkactive", "connections", "connections_in", "connections_out", "networks", "relayfee",
        "incrementalfee", "localaddresses", "warnings"})
public class DtoNetworkInfo {
    private Integer version;
    @JsonProperty("subversion")
    private String subVersion;
    @JsonProperty("protocolversion")
    private Integer protocolVersion;
    @JsonProperty("localservices")
    private String localServices;
    @JsonProperty("localservicesnames")
    private List<ServiceFlag> localServicesNames;
    @JsonProperty("localrelay")
    private Boolean localRelay;
    @JsonProperty("timeoffset")
    private Integer timeOffset;
    @JsonProperty("networkactive")
    private Boolean networkActive;
    private Integer connections;
    @JsonProperty("connections_in")
    private Integer connectionsIn;
    @JsonProperty("connections_out")
    private Integer connectionsOut;
    private List<Network> networks;
    @JsonProperty("relayfee")
    private Double relayFee;
    @JsonProperty("incrementalfee")
    private Double incrementalFee;
    @JsonProperty("localaddresses")
    private List<LocalAddress> localAddresses;
    private String warnings;

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"name", "limited", "reachable", "proxy", "proxy_randomize_credentials"})
    public static class Network {
        private NetworkType name;
        private Boolean limited;
        private Boolean reachable;
        private String proxy;
        @JsonProperty("proxy_randomize_credentials")
        private Boolean proxyRandomizeCredentials;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"address", "port", "score"})
    public static class LocalAddress {
        private String address;
        private Integer port;
        private Integer score;
    }

    @RequiredArgsConstructor
    public enum NetworkType {
        IPV4("ipv4"), IPV6("ipv6"), ONION("onion"), I2P("i2p");

        @Getter(onMethod_ = @JsonValue)
        private final String name;
    }

    @RequiredArgsConstructor
    public enum ServiceFlag {
        @JsonEnumDefaultValue
        UNKNOWN(0),
        // Taken from https://github.com/bitcoin/bitcoin/blob/master/src/protocol.h:
        NETWORK(1),
        BLOOM(1 << 2),
        WITNESS(1 << 3),
        COMPACT_FILTERS(1 << 6),
        NETWORK_LIMITED(1 << 10);

        @Getter
        private final int value;
    }
}
