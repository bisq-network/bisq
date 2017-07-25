clear
curl -s http://localhost:8080/api/v1/offer_detail?offer_id=$1 | python -m json.tool | less
