package bisq.restapi.endpoints;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.Proposal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;



import bisq.restapi.RestApi;
import bisq.restapi.RestApiMain;
import bisq.restapi.dto.BsqStatsDto;
import bisq.restapi.dto.JsonDaoCycle;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Slf4j
@Path("/explorer/dao")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "EXPLORER API")
public class ExplorerDaoApi {
    private final DaoStateService daoStateService;
    private final DaoFacade daoFacade;
    private final ProposalService proposalService;
    private final CycleService cycleService;
    private final RestApi restApi;

    public ExplorerDaoApi(@Context Application application) {
        restApi = ((RestApiMain) application).getRestApi();
        daoStateService = restApi.getDaoStateService();
        proposalService = restApi.getProposalService();
        cycleService = restApi.getCycleService();
        daoFacade = restApi.getDaoFacade();
    }

    //http://localhost:8081/api/v1/explorer/dao/get-bsq-stats
    @GET
    @Path("get-bsq-stats")
    public BsqStatsDto getBsqStats() {
        restApi.checkDaoReady();
        long genesisSupply = daoFacade.getGenesisTotalSupply().getValue();
        long issuedByCompensations = daoStateService.getIssuanceSetForType(IssuanceType.COMPENSATION).stream().mapToLong(Issuance::getAmount).sum();
        long issuedByReimbursements = daoStateService.getIssuanceSetForType(IssuanceType.REIMBURSEMENT).stream().mapToLong(Issuance::getAmount).sum();
        long minted = genesisSupply + issuedByCompensations + issuedByReimbursements;
        long burnt = daoStateService.getTotalAmountOfBurntBsq();
        int unspentTxos = daoStateService.getUnspentTxOutputMap().size();
        int spentTxos = daoStateService.getSpentInfoMap().size();
        int numAddresses = daoStateService.getTxIdSetByAddress().size();
        log.info("client requested BSQ stats, height={}", daoFacade.getChainHeight());
        return new BsqStatsDto(minted, burnt, numAddresses, unspentTxos, spentTxos,
                daoFacade.getChainHeight(), daoFacade.getGenesisBlockHeight());
    }

    @GET
    @Path("query-dao-cycles")
    public List<JsonDaoCycle> queryDaoCycles() {
        restApi.checkDaoReady();
        Set<Integer> cyclesAdded = new HashSet<>();
        List<JsonDaoCycle> result = new ArrayList<>();
        // Creating our data structure is a bit expensive so we ensure to only create the CycleListItems once.
        daoStateService.getCycles().stream()
                .filter(cycle -> !cyclesAdded.contains(cycle.getHeightOfFirstBlock()))
                .filter(cycle -> cycleService.getCycleIndex(cycle) >= 0)    // change this if you only need the latest n cycles
                .forEach(cycle -> {
                    long cycleStartTime = daoStateService.getBlockTimeAtBlockHeight(cycle.getHeightOfFirstBlock());
                    int cycleIndex = cycleService.getCycleIndex(cycle);
                    boolean isCycleInProgress = cycleService.isBlockHeightInCycle(daoFacade.getChainHeight(), cycle);
                    log.info("Cycle {} {}", cycleIndex, isCycleInProgress ? "pending" : "complete");
                    List<Proposal> proposalsForCycle = proposalService.getValidatedProposals().stream()
                            .filter(proposal -> cycleService.isTxInCycle(cycle, proposal.getTxId()))
                            .collect(Collectors.toList());
                    int tempProposalCount = 0;
                    if (isCycleInProgress) {
                        tempProposalCount = (int) proposalService.getTempProposalsAsArrayList().stream()
                                .filter(proposal -> cycleService.isTxInCycle(cycle, proposal.getTxId()))
                                .count();
                    }

                    long burnedAmount = daoFacade.getBurntFeeTxs().stream()
                            .filter(e -> cycleService.isBlockHeightInCycle(e.getBlockHeight(), cycle))
                            .mapToLong(Tx::getBurntFee)
                            .sum();

                    int proposalCount = proposalsForCycle.size() + tempProposalCount;
                    long issuedAmount = daoFacade.getIssuanceForCycle(cycle);
                    JsonDaoCycle resultsOfCycle = new JsonDaoCycle(
                            cycle.getHeightOfFirstBlock(),
                            cycleIndex + 1,
                            cycleStartTime,
                            proposalCount,
                            burnedAmount,
                            issuedAmount,
                            isCycleInProgress);
                    cyclesAdded.add(resultsOfCycle.getHeightOfFirstBlock());
                    result.add(resultsOfCycle);
                });
        result.sort(Comparator.comparing(e -> ((JsonDaoCycle) e).getCycleIndex()).reversed());
        log.info("client requested dao cycles, returning {} records", result.size());
        return result;
    }
}
