clear
curl -X DELETE -s http://localhost:8080/api/v1/offer_cancel?offer_id=$1 | python -m json.tool | less
