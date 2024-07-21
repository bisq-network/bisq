package bisq.restapi.endpoints;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;



import bisq.restapi.BlockDataToJsonConverter;
import bisq.restapi.RestApi;
import bisq.restapi.RestApiMain;
import bisq.restapi.dto.JsonBlock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Slf4j
@Path("/explorer/blocks")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "BLOCKS API")
public class ExplorerBlocksApi {
    private final DaoStateService daoStateService;
    private final RestApi restApi;

    public ExplorerBlocksApi(@Context Application application) {
        restApi = ((RestApiMain) application).getRestApi();
        daoStateService = restApi.getDaoStateService();
    }

    // http://localhost:8081/api/v1/explorer/blocks/get-bsq-block-by-height/139
    @Operation(description = "Request BSQ block details")
    @ApiResponse(responseCode = "200", description = "The BSQ block",
            content = {@Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(allOf = JsonBlock.class))}
    )
    @GET
    @Path("get-bsq-block-by-height/{block-height}")
    public JsonBlock getBsqBlockByHeight(@Parameter(description = "Block Height") @PathParam("block-height") int blockHeight) {
        restApi.checkDaoReady();
        List<Block> blocks = daoStateService.getBlocks();
        Optional<JsonBlock> jsonBlock = checkNotNull(blocks.stream())
                .filter(block -> block.getHeight() == blockHeight)
                .map(block -> BlockDataToJsonConverter.getJsonBlock(daoStateService, block))
                .findFirst();
        if (jsonBlock.isPresent()) {
            log.info("supplying block at height {} to client.", blockHeight);
            return jsonBlock.get();
        }
        log.warn("block {} not found!", blockHeight);
        return null;
    }

    //http://localhost:8081/api/v1/explorer/blocks/get-bsq-block-by-hash/2e90186bd0958e8d4821e0b2546e018d70e3b4f136af8676e3571ca2363ce7f8
    @GET
    @Path("get-bsq-block-by-hash/{block-hash}")
    public JsonBlock getBsqBlockByHash(@Parameter(description = "Block Hash") @PathParam("block-hash") String hash) {
        restApi.checkDaoReady();
        List<Block> blocks = daoStateService.getBlocks();
        Optional<JsonBlock> jsonBlock = checkNotNull(blocks.stream())
                .filter(block -> block.getHash().equalsIgnoreCase(hash))
                .map(block -> BlockDataToJsonConverter.getJsonBlock(daoStateService, block))
                .findFirst();
        if (jsonBlock.isPresent()) {
            log.info("supplying block {} to client.", hash);
            return jsonBlock.get();
        }
        log.warn("block {} not found!", hash);
        return null;
    }
}
