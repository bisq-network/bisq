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

package bisq.daemon.grpc;

import bisq.core.api.CoreApi;
import bisq.core.api.CoreDaoService;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.governance.myvote.MyVote;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.ConfiscateBondProposal;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ProposalVoteResult;
import bisq.core.dao.state.model.governance.ReimbursementProposal;
import bisq.core.dao.state.model.governance.RemoveAssetProposal;
import bisq.core.dao.state.model.governance.RoleProposal;
import bisq.core.dao.state.model.governance.Vote;

import bisq.proto.grpc.BallotInfo;
import bisq.proto.grpc.BondedRoleInfo;
import bisq.proto.grpc.CreateBondedRoleProposalRequest;
import bisq.proto.grpc.CreateChangeParamProposalRequest;
import bisq.proto.grpc.CreateCompensationProposalRequest;
import bisq.proto.grpc.CreateConfiscateBondProposalRequest;
import bisq.proto.grpc.CreateGenericProposalRequest;
import bisq.proto.grpc.CreateProposalReply;
import bisq.proto.grpc.CreateReimbursementProposalRequest;
import bisq.proto.grpc.CreateRemoveAssetProposalRequest;
import bisq.proto.grpc.CycleInfo;
import bisq.proto.grpc.DaoPhaseEnum;
import bisq.proto.grpc.EvaluatedProposalInfo;
import bisq.proto.grpc.GetBallotsReply;
import bisq.proto.grpc.GetBallotsRequest;
import bisq.proto.grpc.GetBondedRolesReply;
import bisq.proto.grpc.GetBondedRolesRequest;
import bisq.proto.grpc.GetCycleInfoReply;
import bisq.proto.grpc.GetCycleInfoRequest;
import bisq.proto.grpc.GetCyclesReply;
import bisq.proto.grpc.GetCyclesRequest;
import bisq.proto.grpc.GetDaoParamValueReply;
import bisq.proto.grpc.GetDaoParamValueRequest;
import bisq.proto.grpc.GetMyVotesReply;
import bisq.proto.grpc.GetMyVotesRequest;
import bisq.proto.grpc.GetProposalsReply;
import bisq.proto.grpc.GetProposalsRequest;
import bisq.proto.grpc.GetRawTransactionReply;
import bisq.proto.grpc.GetRawTransactionRequest;
import bisq.proto.grpc.GetVoteResultsReply;
import bisq.proto.grpc.GetVoteResultsRequest;
import bisq.proto.grpc.MyVoteInfo;
import bisq.proto.grpc.ProposalInfo;
import bisq.proto.grpc.PublishBlindVoteReply;
import bisq.proto.grpc.PublishBlindVoteRequest;
import bisq.proto.grpc.SetVoteReply;
import bisq.proto.grpc.SetVoteRequest;

import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static bisq.daemon.grpc.interceptor.GrpcServiceRateMeteringConfig.getCustomRateMeteringInterceptor;
import static bisq.proto.grpc.DaoGrpc.DaoImplBase;
import static bisq.proto.grpc.DaoGrpc.getCreateBondedRoleProposalMethod;
import static bisq.proto.grpc.DaoGrpc.getCreateChangeParamProposalMethod;
import static bisq.proto.grpc.DaoGrpc.getCreateCompensationProposalMethod;
import static bisq.proto.grpc.DaoGrpc.getCreateConfiscateBondProposalMethod;
import static bisq.proto.grpc.DaoGrpc.getCreateGenericProposalMethod;
import static bisq.proto.grpc.DaoGrpc.getCreateReimbursementProposalMethod;
import static bisq.proto.grpc.DaoGrpc.getCreateRemoveAssetProposalMethod;
import static bisq.proto.grpc.DaoGrpc.getGetRawTransactionMethod;
import static bisq.proto.grpc.DaoGrpc.getPublishBlindVoteMethod;
import static bisq.proto.grpc.DaoGrpc.getSetVoteMethod;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.daemon.grpc.interceptor.CallRateMeteringInterceptor;
import bisq.daemon.grpc.interceptor.GrpcCallRateMeter;

@Slf4j
class GrpcDaoService extends DaoImplBase {

    private final CoreApi coreApi;
    private final GrpcExceptionHandler exceptionHandler;

    @Inject
    public GrpcDaoService(CoreApi coreApi, GrpcExceptionHandler exceptionHandler) {
        this.coreApi = coreApi;
        this.exceptionHandler = exceptionHandler;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reads
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void getCycleInfo(GetCycleInfoRequest req, StreamObserver<GetCycleInfoReply> obs) {
        try {
            CoreDaoService.CycleSnapshot s = coreApi.getDaoCycleSnapshot();
            GetCycleInfoReply reply = GetCycleInfoReply.newBuilder()
                    .setCycleIndex(s.cycleIndex)
                    .setChainHeight(s.chainHeight)
                    .setPhase(toPhaseEnum(s.phase))
                    .setFirstBlockOfCycle(s.firstBlockOfCycle)
                    .setLastBlockOfCycle(s.lastBlockOfCycle)
                    .setFirstBlockOfPhase(s.firstBlockOfPhase)
                    .setLastBlockOfPhase(s.lastBlockOfPhase)
                    .setDurationOfPhase(s.durationOfPhase)
                    .setBlocksRemainingInPhase(s.blocksRemainingInPhase)
                    .setIsDaoStateInSync(s.isDaoStateInSync)
                    .setNumBlindVotesInCurrentCycle(s.numBlindVotesInCurrentCycle)
                    .setNumProposalsInCurrentCycle(s.numProposalsInCurrentCycle)
                    .build();
            obs.onNext(reply);
            obs.onCompleted();
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void getCycles(GetCyclesRequest req, StreamObserver<GetCyclesReply> obs) {
        try {
            GetCyclesReply.Builder b = GetCyclesReply.newBuilder();
            List<Cycle> cycles = coreApi.getDaoCycles();
            for (Cycle c : cycles) {
                b.addCycles(CycleInfo.newBuilder()
                        .setCycleIndex(coreApi.getDaoCycleIndex(c))
                        .setHeightOfFirstBlock(c.getHeightOfFirstBlock())
                        .setHeightOfLastBlock(c.getHeightOfLastBlock())
                        .setDuration(c.getDuration())
                        .build());
            }
            obs.onNext(b.build());
            obs.onCompleted();
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void getProposals(GetProposalsRequest req, StreamObserver<GetProposalsReply> obs) {
        try {
            List<Proposal> list;
            switch (req.getFilter()) {
                case MY:
                    list = coreApi.getDaoMyProposals();
                    break;
                case ALL:
                    list = coreApi.getDaoAllValidatedProposals();
                    break;
                case FOR_CYCLE:
                    list = coreApi.getDaoProposalsForCycle(req.getCycleIndex());
                    break;
                case ACTIVE_OR_MY_UNCONFIRMED:
                case UNRECOGNIZED:
                default:
                    list = coreApi.getDaoActiveOrMyUnconfirmedProposals();
            }
            GetProposalsReply.Builder b = GetProposalsReply.newBuilder();
            for (Proposal p : list) {
                b.addProposals(toProposalInfo(p));
            }
            obs.onNext(b.build());
            obs.onCompleted();
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void getBallots(GetBallotsRequest req, StreamObserver<GetBallotsReply> obs) {
        try {
            GetBallotsReply.Builder b = GetBallotsReply.newBuilder();
            for (Ballot ballot : coreApi.getDaoBallots()) {
                BallotInfo.Builder bb = BallotInfo.newBuilder()
                        .setProposal(toProposalInfo(ballot.getProposal()))
                        .setHasVote(ballot.getVote() != null);
                Vote v = ballot.getVote();
                if (v == null) {
                    bb.setVote("ignore");
                } else {
                    bb.setVote(v.isAccepted() ? "accept" : "reject");
                    bb.setVoteAccept(v.isAccepted());
                }
                b.addBallots(bb.build());
            }
            obs.onNext(b.build());
            obs.onCompleted();
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void getMyVotes(GetMyVotesRequest req, StreamObserver<GetMyVotesReply> obs) {
        try {
            GetMyVotesReply.Builder b = GetMyVotesReply.newBuilder();
            for (MyVote mv : coreApi.getDaoMyVotes()) {
                b.addVotes(MyVoteInfo.newBuilder()
                        .setDate(mv.getDate())
                        .setBlindVoteTxId(Optional.ofNullable(mv.getBlindVoteTxId()).orElse(""))
                        .setRevealTxId(Optional.ofNullable(mv.getRevealTxId()).orElse(""))
                        .setStake(mv.getBlindVote().getStake())
                        .setCycleIndex(coreApi.getDaoCycleSnapshot().cycleIndex)
                        .build());
            }
            obs.onNext(b.build());
            obs.onCompleted();
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void getVoteResults(GetVoteResultsRequest req, StreamObserver<GetVoteResultsReply> obs) {
        try {
            GetVoteResultsReply.Builder b = GetVoteResultsReply.newBuilder();
            for (EvaluatedProposal ep : coreApi.getDaoVoteResults(req.getCycleIndex())) {
                ProposalVoteResult vr = ep.getProposalVoteResult();
                b.addEvaluatedProposals(EvaluatedProposalInfo.newBuilder()
                        .setProposal(toProposalInfo(ep.getProposal()))
                        .setIsAccepted(ep.isAccepted())
                        .setRequiredQuorum(vr.getQuorum())
                        .setRequiredThreshold(vr.getThreshold())
                        .setStakeOfAllVotes(vr.getStakeOfAcceptedVotes() + vr.getStakeOfRejectedVotes())
                        .setNumAcceptedVotes(vr.getNumAcceptedVotes())
                        .setNumRejectedVotes(vr.getNumRejectedVotes())
                        .setNumIgnoredVotes(vr.getNumIgnoredVotes())
                        .build());
            }
            obs.onNext(b.build());
            obs.onCompleted();
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void getBondedRoles(GetBondedRolesRequest req, StreamObserver<GetBondedRolesReply> obs) {
        try {
            GetBondedRolesReply.Builder b = GetBondedRolesReply.newBuilder();
            for (BondedRole br : coreApi.getDaoBondedRoles()) {
                BondedRoleType type = br.getBondedAsset().getBondedRoleType();
                b.addRoles(BondedRoleInfo.newBuilder()
                        .setBondedRoleType(type.name())
                        .setName(br.getBondedAsset().getName())
                        .setLink(br.getBondedAsset().getLink())
                        .setIsAccepted(br.isActive())
                        .setRequiredBond(coreApi.getDaoRequiredBond(type))
                        .build());
            }
            obs.onNext(b.build());
            obs.onCompleted();
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void getDaoParamValue(GetDaoParamValueRequest req, StreamObserver<GetDaoParamValueReply> obs) {
        try {
            obs.onNext(GetDaoParamValueReply.newBuilder()
                    .setValue(coreApi.getDaoParamValue(req.getParam()))
                    .build());
            obs.onCompleted();
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Writes
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void replyOnPublish(Proposal p, StreamObserver<CreateProposalReply> obs) {
        obs.onNext(CreateProposalReply.newBuilder().setProposal(toProposalInfo(p)).build());
        obs.onCompleted();
    }

    private void replyOnPublishError(String msg, StreamObserver<?> obs) {
        exceptionHandler.handleException(log, new IllegalStateException(msg), obs);
    }

    @Override
    public void createCompensationProposal(CreateCompensationProposalRequest req,
                                           StreamObserver<CreateProposalReply> obs) {
        try {
            coreApi.daoCreateCompensationProposal(
                    req.getName(), req.getLink(), req.getRequestedBsq(),
                    req.getBurningManReceiverAddress(),
                    p -> replyOnPublish(p, obs),
                    msg -> replyOnPublishError(msg, obs));
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void createReimbursementProposal(CreateReimbursementProposalRequest req,
                                            StreamObserver<CreateProposalReply> obs) {
        try {
            coreApi.daoCreateReimbursementProposal(
                    req.getName(), req.getLink(), req.getRequestedBsq(),
                    p -> replyOnPublish(p, obs),
                    msg -> replyOnPublishError(msg, obs));
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void createChangeParamProposal(CreateChangeParamProposalRequest req,
                                          StreamObserver<CreateProposalReply> obs) {
        try {
            coreApi.daoCreateChangeParamProposal(
                    req.getName(), req.getLink(), req.getParam(), req.getParamValue(),
                    p -> replyOnPublish(p, obs),
                    msg -> replyOnPublishError(msg, obs));
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void createBondedRoleProposal(CreateBondedRoleProposalRequest req,
                                         StreamObserver<CreateProposalReply> obs) {
        try {
            coreApi.daoCreateBondedRoleProposal(
                    req.getBondedRoleType(), req.getName(), req.getLink(),
                    p -> replyOnPublish(p, obs),
                    msg -> replyOnPublishError(msg, obs));
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void createConfiscateBondProposal(CreateConfiscateBondProposalRequest req,
                                             StreamObserver<CreateProposalReply> obs) {
        try {
            coreApi.daoCreateConfiscateBondProposal(
                    req.getName(), req.getLink(), req.getLockupTxId(),
                    p -> replyOnPublish(p, obs),
                    msg -> replyOnPublishError(msg, obs));
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void createGenericProposal(CreateGenericProposalRequest req,
                                      StreamObserver<CreateProposalReply> obs) {
        try {
            coreApi.daoCreateGenericProposal(req.getName(), req.getLink(),
                    p -> replyOnPublish(p, obs),
                    msg -> replyOnPublishError(msg, obs));
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void createRemoveAssetProposal(CreateRemoveAssetProposalRequest req,
                                          StreamObserver<CreateProposalReply> obs) {
        try {
            coreApi.daoCreateRemoveAssetProposal(
                    req.getName(), req.getLink(), req.getAssetCode(),
                    p -> replyOnPublish(p, obs),
                    msg -> replyOnPublishError(msg, obs));
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void setVote(SetVoteRequest req, StreamObserver<SetVoteReply> obs) {
        try {
            coreApi.daoSetVote(req.getProposalTxId(), req.getVote());
            obs.onNext(SetVoteReply.newBuilder().build());
            obs.onCompleted();
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void publishBlindVote(PublishBlindVoteRequest req, StreamObserver<PublishBlindVoteReply> obs) {
        try {
            coreApi.daoPublishBlindVote(req.getStake(),
                    txId -> {
                        obs.onNext(PublishBlindVoteReply.newBuilder().setBlindVoteTxId(txId).build());
                        obs.onCompleted();
                    },
                    msg -> replyOnPublishError(msg, obs));
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    @Override
    public void getRawTransaction(GetRawTransactionRequest req, StreamObserver<GetRawTransactionReply> obs) {
        try {
            String hex = coreApi.daoGetRawTransactionHex(req.getTxId());
            obs.onNext(GetRawTransactionReply.newBuilder().setRawTxHex(hex).build());
            obs.onCompleted();
        } catch (Throwable t) {
            exceptionHandler.handleException(log, t, obs);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mappers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static ProposalInfo toProposalInfo(Proposal p) {
        ProposalInfo.Builder b = ProposalInfo.newBuilder()
                .setTxId(Optional.ofNullable(p.getTxId()).orElse(""))
                .setProposalType(p.getType().name())
                .setName(Optional.ofNullable(p.getName()).orElse(""))
                .setLink(Optional.ofNullable(p.getLink()).orElse(""))
                .setVersion(String.valueOf(p.getVersion()))
                .setCreationDate(p.getCreationDate());
        if (p instanceof CompensationProposal) {
            CompensationProposal cp = (CompensationProposal) p;
            b.setRequestedBsq(cp.getRequestedBsq().value);
            cp.getBurningManReceiverAddress().ifPresent(b::setBurningManReceiverAddress);
        } else if (p instanceof ReimbursementProposal) {
            b.setRequestedBsq(((ReimbursementProposal) p).getRequestedBsq().value);
        } else if (p instanceof ChangeParamProposal) {
            ChangeParamProposal cpp = (ChangeParamProposal) p;
            b.setParam(cpp.getParam().name());
            b.setParamValue(cpp.getParamValue());
        } else if (p instanceof RoleProposal) {
            b.setBondedRoleType(((RoleProposal) p).getRole().getBondedRoleType().name());
        } else if (p instanceof ConfiscateBondProposal) {
            b.setLockupTxId(((ConfiscateBondProposal) p).getLockupTxId());
        } else if (p instanceof RemoveAssetProposal) {
            b.setAssetCode(((RemoveAssetProposal) p).getTickerSymbol());
        }
        // GenericProposal: no extra fields.
        return b.build();
    }

    private static DaoPhaseEnum toPhaseEnum(DaoPhase.Phase phase) {
        switch (phase) {
            case PROPOSAL:    return DaoPhaseEnum.DAO_PHASE_PROPOSAL;
            case BREAK1:      return DaoPhaseEnum.DAO_PHASE_BREAK1;
            case BLIND_VOTE:  return DaoPhaseEnum.DAO_PHASE_BLIND_VOTE;
            case BREAK2:      return DaoPhaseEnum.DAO_PHASE_BREAK2;
            case VOTE_REVEAL: return DaoPhaseEnum.DAO_PHASE_VOTE_REVEAL;
            case BREAK3:      return DaoPhaseEnum.DAO_PHASE_BREAK3;
            case RESULT:      return DaoPhaseEnum.DAO_PHASE_RESULT;
            case UNDEFINED:
            default:          return DaoPhaseEnum.DAO_PHASE_UNDEFINED;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interceptors
    ///////////////////////////////////////////////////////////////////////////////////////////

    final ServerInterceptor[] interceptors() {
        Optional<ServerInterceptor> rateMeteringInterceptor = rateMeteringInterceptor();
        return rateMeteringInterceptor.map(si -> new ServerInterceptor[]{si})
                .orElseGet(() -> new ServerInterceptor[0]);
    }

    final Optional<ServerInterceptor> rateMeteringInterceptor() {
        return getCustomRateMeteringInterceptor(coreApi.getConfig().appDataDir, this.getClass())
                .or(() -> Optional.of(CallRateMeteringInterceptor.valueOf(
                        new HashMap<>() {{
                            // Allow tests to drive many proposals/votes per second. These
                            // are intentionally generous; CallRateMeteringConfig overrides
                            // can tighten them in production deployments.
                            put(getCreateCompensationProposalMethod().getFullMethodName(),
                                    new GrpcCallRateMeter(100, SECONDS));
                            put(getCreateReimbursementProposalMethod().getFullMethodName(),
                                    new GrpcCallRateMeter(100, SECONDS));
                            put(getCreateChangeParamProposalMethod().getFullMethodName(),
                                    new GrpcCallRateMeter(100, SECONDS));
                            put(getCreateBondedRoleProposalMethod().getFullMethodName(),
                                    new GrpcCallRateMeter(100, SECONDS));
                            put(getCreateConfiscateBondProposalMethod().getFullMethodName(),
                                    new GrpcCallRateMeter(100, SECONDS));
                            put(getCreateGenericProposalMethod().getFullMethodName(),
                                    new GrpcCallRateMeter(100, SECONDS));
                            put(getCreateRemoveAssetProposalMethod().getFullMethodName(),
                                    new GrpcCallRateMeter(100, SECONDS));
                            put(getSetVoteMethod().getFullMethodName(),
                                    new GrpcCallRateMeter(200, SECONDS));
                            put(getPublishBlindVoteMethod().getFullMethodName(),
                                    new GrpcCallRateMeter(100, SECONDS));
                            put(getGetRawTransactionMethod().getFullMethodName(),
                                    new GrpcCallRateMeter(500, SECONDS));
                        }}
                )));
    }
}
