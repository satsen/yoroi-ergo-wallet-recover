# Yoroi Ergo Wallet Recover

Yoroi's centralized servers for Ergo features have stopped working. They do not provide any support and it has never made it possible for users to retrieve their seed phrase.
If you have your Ergo wallet open in Yoroi and know the password for it, you can use this tool to decrypt the wallet and send all funds to a new wallet of yours.

## Guide
1. Install Java [from here](https://adoptium.net/)

2. Download the Yoroi Ergo Wallet Recover program from [here](https://github.com/satsen/yoroi-ergo-wallet-recover/releases/download/v1.0.1/yoroi-ergo-wallet-recover-1.0.1.jar) (it is large because it includes all the Ergo libraries)

3. Open [this link](wallet.txt), click the download icon and put the file in the same place where the program is

4. Press F12 or CTRL+SHIFT+I

5. 
	- Chrome: Open the Application tab
	- Firefox: Open the Storage tab

6. From the sidebar, select IndexedDB

7. 
	- Chrome: Expand `yoroi-schema`
	- Firefox: Expand `moz-extension://...` and then `yoroi-schema`

8. From there, select `Key`

9. In the data table, expand the values and look for the `IsEncrypted: true` one

10. Copy the `hash` value of that one and put it into the `key` field of the [wallet.txt](wallet.txt) file

11. In the [wallet.txt](wallet.txt) file, also enter the password that you were using for the wallet

12. Then, enter the destination address to your new wallet that you have made in another program (not Yoroi) where everything will be sent to

### 13. Address count
If you used multiple different receive addresses, you need to specify how many you used. If you are not sure, you can specify something like 10 or 100.

### 14. Donation
The default donation percentage is 5%, which means that 5% of the ergo coins (none of the tokens) in your wallet will be sent to the tool's developer.

If you do not want to donate anything, you can set this field to 0%.

### 15. Run the program

Finally, open the terminal (PowerShell on Windows) and run the program with `java -jar yoroi-ergo-wallet-recover-v1.0.1.jar`

## Support
If you need help with anything, feel free to join the [Satergo Telegram chat](https://t.me/Satergo) and make sure to not reply to any direct messages sent to you.

## Run from source

If you want to run the program from the source code, use the `./gradlew run` command. To build the program JAR, use the `./gradlew build` command.