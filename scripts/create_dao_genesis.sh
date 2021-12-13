#!/usr/bin/env bash
# This script can be used to create a Bisq DAO genesis transaction on either regtest or testnet.
# Requirements:
#  - bc and jq must be installed (e.g. sudo apt install bc jq)

set -e

BTC_NETWORK=regtest
GENESIS_BSQ_AMOUNT=0
GENESIS_BSQ_DISTRIBUTION=()
BITCOIND_CONFIG=/var/lib/bitcoind/bitcoin.conf

function show_help() {
    cat << END
Usage: ${0##*/} [-h] [-c CONF_FILE] [-n NETWORK] [GENESIS_BSQ_AMOUNT] [GENESIS_BSQ_DISTRIBUTION]

GENESIS_BSQ_AMOUNT
    Total amount of BSQ to include in the genesis transaction (if not specified, will be prompted).

GENESIS_BSQ_DISTRIBUTION
    Distribution of BSQ within the genesis transaction [bsq_amount:bsq_address,...] (if not specified, will be prompted).

-h --help
    Display this help message and exit.

-c --conf <CONF_FILE>
    Path to bitcoind configuration file (default is /var/lib/bitcoind/bitcoin.conf).

-n --network <NETWORK>
    Bitcoin network [REGTEST|TESTNET] (default is REGTEST).
END
}

function read_input() {
    while true; do
        read input
        if [[ ${input} =~ $1 ]]; then
            echo "${input}"
            break
        fi
        echo >&2 "Invalid input, try again"
    done
}

function generate_prevtx_json() {
	json_file="prevtxs.json"
	local tx_data=$1
	local txid=$2
	local vout=$(echo ${tx_data} | jq '.n')
	local scriptPubkey=$(echo ${tx_data} | jq '.scriptPubKey.hex')
	local amount=$(echo ${tx_data} | jq '.value')
	echo -en "[{\n" > ${json_file}
	echo -en "      \"txid\": \"${txid}\",\n" >> ${json_file}
	echo -en "      \"vout\": ${vout},\n" >> ${json_file}
	echo -en "      \"scriptPubKey\": ${scriptPubkey},\n" >> ${json_file}
	echo -en "      \"amount\": ${amount}\n" >> ${json_file}
	echo -en "}]\n" >> ${json_file}
}

function generate_privkeys_json() {
	json_file="privatekeys.json"
	local address=$1
	local privkey=$(${BITCOIN_CLI} dumpprivkey ${address})
	echo -en "[\"$privkey\"]\n" > ${json_file}
}

while (( $# )); do
    case ${1:-} in
        -h|-\?|--help)
            show_help
            exit
            ;;
        -c|--conf)
            if [[ -f "$2" ]]; then
                BITCOIND_CONFIG=$2
                shift
            else
                echo "ERROR: Specified 'conf' file does not exist"
                exit 1
            fi
            ;;
        -n|--network)
            if [[ $2 =~ ^REGTEST|TESTNET$ ]]; then
                BTC_NETWORK=$2
                shift
            else
                echo "ERROR: Specified 'network' is not valid, must be REGTEST or TESTNET"
                exit 1
            fi
            ;;
        --)              # End of all options
            shift
            break
            ;;
        -?*)
            printf "ERROR: Unknown option: %s\n" "$1"
            exit 1
            ;;
        *)               # Default case; no more options, so break out of the loop
            break
    esac
    shift
done

if [[ $1 =~ ^[0-9]+\.?[0-9]*$ ]]; then
    GENESIS_BSQ_AMOUNT=$1
elif [[ "$1" ]]; then
    echo "ERROR: Invalid BSQ amount"
    exit 1
fi

if [[ $2 =~ ^([0-9]+\.?[0-9]*:B.+)(,[0-9]+\.?[0-9]*:B.+)*$ ]]; then
    IFS=',' read -r -a GENESIS_BSQ_DISTRIBUTION <<< "$2"
elif [[ "$2" ]]; then
    echo "ERROR: Invalid BSQ distribution format, must be [bsq_amount:bsq_address,...]"
    exit 1
fi

BITCOIN_CLI="bitcoin-cli -conf=${BITCOIND_CONFIG}"
BITCOIN_TX="bitcoin-tx -${BTC_NETWORK}"

${BITCOIN_CLI} getblockcount &>/dev/null
if [[ $? -eq 1 ]]; then
	echo "ERROR: bitcoind must be running"
	exit 1
fi

if (( $(echo "${GENESIS_BSQ_AMOUNT} == 0" | bc -l) )); then
    echo "How much BSQ would you like to distribute in the genesis transaction?"
    GENESIS_BSQ_AMOUNT=$(read_input "^[0-9]+\.?[0-9]*$")
fi

GENESIS_BTC_AMOUNT=$(awk "BEGIN {printf \"%.8f\",${GENESIS_BSQ_AMOUNT}/1000000.00}")
GENESIS_BTC_FUNDING_AMOUNT=$(awk "BEGIN {printf \"%.8f\",${GENESIS_BTC_AMOUNT}+0.0001}")

btc_balance=$(${BITCOIN_CLI} getbalance)
if (( $(echo "${btc_balance} < ${GENESIS_BTC_FUNDING_AMOUNT}" | bc -l) )); then
	printf "ERROR: Insufficient balance; %'.8f BTC is required but only %'.8f BTC is available\n" ${GENESIS_BTC_FUNDING_AMOUNT} ${btc_balance}
	exit 1
fi

distributed_bsq_amount=0
if [[ ${#GENESIS_BSQ_DISTRIBUTION[@]} -eq 0 ]]; then
    printf "How many contributors would you like to include in the genesis transaction? (totaling %'.2f BSQ)\n" ${GENESIS_BSQ_AMOUNT}
    contributor_count=$(read_input "^[0-9]+$")
    for (( i = 1; i <= ${contributor_count}; ++i )); do
        echo "Enter the BSQ address of contributor ${i}:"
        bsq_address=$(read_input "^B.+$")
        echo "Enter the amount of BSQ for contributor ${i}:"
        bsq_amount=$(read_input "^[0-9]+\.?[0-9]*$")
        GENESIS_BSQ_DISTRIBUTION+=("${bsq_amount}:${bsq_address}")
        distributed_bsq_amount=$(awk "BEGIN {printf \"%.2f\",${distributed_bsq_amount}+${bsq_amount}}")
    done
else
    for item in "${GENESIS_BSQ_DISTRIBUTION[@]}"; do
        bsq_amount="${item%%:*}"
        distributed_bsq_amount=$(awk "BEGIN {printf \"%.2f\",${distributed_bsq_amount}+${bsq_amount}}")
    done
fi
if (( $(echo "${distributed_bsq_amount} != ${GENESIS_BSQ_AMOUNT}" | bc -l) )); then
    printf "ERROR: The BSQ amount being distributed is %'.2f but must total %'.2f\n" ${distributed_bsq_amount} ${GENESIS_BSQ_AMOUNT}
    exit 1
fi

genesis_input_address=$(${BITCOIN_CLI} getnewaddress "Genesis funding address")
printf "Sending %'.8f BTC to genesis funding address ${genesis_input_address}\n" ${GENESIS_BTC_FUNDING_AMOUNT}
genesis_input_txid=$(${BITCOIN_CLI} sendtoaddress ${genesis_input_address} ${GENESIS_BTC_FUNDING_AMOUNT})
echo "Genesis funding txid is ${genesis_input_txid}"

echo "Creating genesis transaction"
tx_hex=$(${BITCOIN_CLI} gettransaction ${genesis_input_txid} | jq '.hex'|tr -d '"')
vin_json=$(${BITCOIN_CLI} decoderawtransaction ${tx_hex} | jq ".vout | map(select(.value==${GENESIS_BTC_FUNDING_AMOUNT}))[0]" | tr -d "[ \n\t]")
vout=$(echo ${vin_json}|jq '.n')

generate_prevtx_json ${vin_json} ${genesis_input_txid}
generate_privkeys_json ${genesis_input_address}

outaddr=
for item in "${GENESIS_BSQ_DISTRIBUTION[@]}"; do
    bsq_amount="${item%%:*}"
    btc_amount="$(awk "BEGIN {printf \"%.8f\",${bsq_amount}/1000000.00}")"
    bsq_address="${item##*:}"
    outaddr="${outaddr}outaddr=${btc_amount}:${bsq_address##B} "
done

genesis_raw=$(${BITCOIN_TX} -create in=${genesis_input_txid}:${vout} ${outaddr} load=prevtxs:prevtxs.json load=privatekeys:privatekeys.json sign=ALL)
echo "The raw genesis transaction is $genesis_raw"

echo "Decoded transaction:"
genesis_decoded=$(${BITCOIN_CLI} decoderawtransaction ${genesis_raw})
echo ${genesis_decoded}| jq '.'
genesis_txid=$(echo ${genesis_decoded}| jq '.txid'|tr -d '"')

echo "Please ensure the above decoded transaction looks valid, and then press Enter to broadcast the genesis transaction (Ctrl+C otherwise)"
read
echo "Genesis txid is $(${BITCOIN_CLI} sendrawtransaction ${genesis_raw})"
