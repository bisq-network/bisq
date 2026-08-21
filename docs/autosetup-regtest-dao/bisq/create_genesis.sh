#!/bin/bash

. ./config.sh

bitcoin_cli="bitcoin-cli -regtest -datadir=.."
echo -en "testing if bitcoind is running: "
$bitcoin_cli getblockcount &>/dev/null
if [ $? -eq 1 ];then
	echo "bitcoind not running, please run it first!"
	exit 1
fi
echo -en "bitcoind is running, all is ok!\n"

echo "Please provide Alice's BSQ Address"
read address_alice
echo "How much BTC to turn to BSQ for Alice? (sum must be 2.5BTC)"
read value_alice
echo "Alice will receive $(echo "scale=2; $value_alice * 1000000.00"|bc -l) BSQ to $address_alice"
echo "Please provide Bob's BSQ Address"
read address_bob
echo "How much BTC to turn to BSQ for Bob? (sum must be 2.5BTC)"
read value_bob
echo "Bob will receive $(echo "scale=2; $value_bob * 1000000.00"|bc) BSQ to $address_bob"


function generate_prevtx_json(){
	json_file="prevtxs.json"
	local tx_data=$1
	local txid=$2
	local vout=$(echo $tx_data | jq '.n') 
	local scriptPubkey=$(echo $tx_data | jq '.scriptPubKey.hex')
	local amount=$(echo $tx_data | jq '.value')
	echo -en "[{\n" > $json_file
	echo -en "      \"txid\": \"${txid}\",\n" >> $json_file
	echo -en "      \"vout\": ${vout},\n" >> $json_file
	echo -en "      \"scriptPubKey\": ${scriptPubkey},\n" >> $json_file
	echo -en "      \"amount\": ${amount}\n" >> $json_file
	echo -en "}]\n" >> $json_file
}

function generate_privkeys_json(){
	json_file="privatekeys.json"
	local address=$1
	local privkey=$($bitcoin_cli dumpprivkey $address)
	echo -en "[\"$privkey\"]\n" > $json_file
	
}

function edit_conf_file() {
	local conf_file="config.sh"
	local genesis_tx=$1
	local genesis_height=$2
	sed -r 's/genesis_tx=.*/genesis_tx="'"$genesis_tx"'"/'  -i $conf_file
	sed -r 's/genesis_height=.*/genesis_height='"$genesis_height"'/' -i  $conf_file
	sed -r 's/dao=.*/dao="true"/' -i $conf_file

}

genesis_input_address=$($bitcoin_cli getnewaddress "genesis tx")
echo "got address $genesis_input_address"

echo "sending 2.5001 BTC to $genesis_input_address"
genesis_input_txid=$($bitcoin_cli sendtoaddress $genesis_input_address 2.5001)
echo "txid is: $genesis_input_txid"

echo "creating genesis tx for you"
tx_hex=$($bitcoin_cli gettransaction $genesis_input_txid | jq '.hex'|tr -d '"')
vin_json=$($bitcoin_cli decoderawtransaction $tx_hex | jq '.vout| map(select(.value==2.5001))[0]'|tr -d "[ \n\t]")
vout=$(echo $vin_json|jq '.n') 

generate_prevtx_json $vin_json $genesis_input_txid
generate_privkeys_json $genesis_input_address

genesis_raw=$(bitcoin-tx -regtest -create in=$genesis_input_txid:$vout outaddr=$value_alice:${address_alice##B} outaddr=$value_bob:${address_bob##B} load=prevtxs:prevtxs.json load=privatekeys:privatekeys.json  sign=ALL)
echo "The raw genesis tx is: $genesis_raw"
echo "Please verify if decoded tx looks ok:"
genesis_decoded=$($bitcoin_cli decoderawtransaction $genesis_raw)
echo $genesis_decoded| jq '.'
genesis_txid=$(echo $genesis_decoded| jq '.txid'|tr -d '"')

echo "Press enter to broadcast transaction, ctrl+c otherwise"
read
echo "Genesis txid is:"
$bitcoin_cli sendrawtransaction $genesis_raw

echo "Mining genesis tx for you"
genesis_block=$($bitcoin_cli generate 1 | jq '.[]'| tr -d '"')
genesis_height=$($bitcoin_cli getblock $genesis_block | jq '.height')
echo "genesis_block is $genesis_block"
echo "genesis_height is $genesis_height"

edit_conf_file $genesis_txid $genesis_height

echo "your conf file was modified accordingly"
echo "DAO setup done"
