import sys, os, json

# Writes a Bisq json F2F payment account form for the given country_code to the current working directory.

if len(sys.argv) < 2:
    print("usage: editf2faccountform.py country_code")
    exit(1)

country_code = str(sys.argv[1]).upper()
acct_form = {
  "_COMMENTS_": [
    "Do not manually edit the paymentMethodId field.",
    "Edit the salt field only if you are recreating a payment account on a new installation and wish to preserve the account age."
  ],
  "paymentMethodId": "F2F",
  "accountName": "Face to Face Payment Account",
  "city": "Anytown",
  "contact": "Me",
  "country": country_code,
  "extraInfo": "",
  "salt": ""
}
target=os.path.dirname(os.path.realpath(__file__)) + '/' + 'f2f-acct.json'
with open (target, 'w') as outfile:
	json.dump(acct_form, outfile, indent=2)
	outfile.write('\n')

exit(0)
