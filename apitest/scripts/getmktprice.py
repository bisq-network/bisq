import sys, requests

# Returns the current BTC price for the given currency_code from a cleartext price service.

if len(sys.argv) < 2:
    print("usage: getmktprice.py currency_code")
    exit(1)

currency_code = str(sys.argv[1]).upper()

url = "https://price.bisq.wiz.biz/getAllMarketPrices"
resp = requests.get(url)
if resp.status_code == 200:
    for i in resp.json()['data']:
        if i['currencyCode'] == currency_code:
            print(int(i['price']))
            break
else:
    print('Error: Could not get ' + currency_code + ' price.')
    exit(1)

exit(0)
