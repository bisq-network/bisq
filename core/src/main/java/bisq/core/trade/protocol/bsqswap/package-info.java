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

package bisq.core.trade.protocol.bsqswap;

/**
 * There are 2 possible pairs of protocols:
 * 1. BuyerAsTaker + SellerAsMaker
 * 2. SellerAsTaker + BuyerAsMaker
 *
 * The Taker always initiates the protocol and sends some basic data like amount, date,
 * feeRate,... to the maker.
 *
 * For the tx creation the buyer/seller perspective is the relevant view.
 * We require an additional message to be sent in the second protocol pair.
 *
 * 1. BuyerAsTaker + SellerAsMaker:
 *
 * BuyerAsTaker:
 * - ApplyFilter
 * - CreateBsqInputsAndChange
 * - SendBsqSwapTakeOfferWithTxInputsMessage
 *
 * SellerAsMaker:
 * - ApplyFilter
 * - ProcessBsqSwapTakeOfferWithTxInputsMessage
 * - SellerAsMakerCreatesAndSignsTx
 * - RemoveOpenOffer
 * - SellerSetupTxListener
 * - SendFinalizeBsqSwapTxRequest
 *
 * BuyerAsTaker:
 * - ProcessFinalizeBsqSwapTxRequest
 * - BuyerFinalizeTx
 * - BuyerPublishesTx
 * - PublishTradeStatistics
 *
 *
 * 2. SellerAsTaker + BuyerAsMaker:
 *
 * SellerAsTaker:
 * - ApplyFilter
 * - SendBsqSwapTakeOfferRequest
 *
 * BuyerAsMaker
 * - ApplyFilter
 * - ProcessBsqSwapTakeOfferRequest
 * - BuyerAsMakerCreatesBsqInputsAndChange
 * - SendBsqSwapTxInputMessage
 *
 * SellerAsTaker:
 * - ProcessBsqSwapTxInputMessage
 * - SellerAsTakerCreatesAndSignsTx
 * - SendFinalizeBsqSwapTxRequest
 *
 * BuyerAsMaker:
 * - ProcessFinalizeBsqSwapTxRequest
 * - BuyerFinalizeTx
 * - BuyerPublishesTx
 * - RemoveOpenOffer
 * - PublishTradeStatistics
 *
 */
