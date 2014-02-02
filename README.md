Payment Tracker

Write a program that keeps a record of payments.  Initially, the application should load the following data from a file:

USD 1000
HKD 100
USD -100
RMB 2000
HKD 200

Once started it should read additional lines from the console. The program should output a grand total of the payments by currency once a minute, e.g.

USD 900
RMB 2000
HKD 300

Detailed Requirements

When your program is run, a filename can be optionally specified. The format of the file will be one or more lines with Currency Code Amount like in the Sample Input above, where the currency may be any uppercase 3 letter code, such as USD, HKD, RMB, NZD, GBP etc. The user can then enter more lines into the console by typing a currency and amount and pressing enter. Once per minute, the output showing the net amounts of each currency should be displayed. If the net amount is 0, that currency should not be displayed. When the user types "quit", the program should exit.

You may need to make some assumptions. For example, if the user enters invalid input, you can choose to display an error message or quit the program.

Optional Bonus Question Allow each currency to have the exchange rate compared to USD configured. When you display the output, write the USD equivalent amount next to it, for example:

USD 900
RMB 2000 (USD 314.60)
HKD 300 (USD 38.62)