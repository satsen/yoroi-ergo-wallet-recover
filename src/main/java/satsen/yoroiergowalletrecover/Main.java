package satsen.yoroiergowalletrecover;

import org.ergoplatform.ErgoAddressEncoder;
import org.ergoplatform.P2PKAddress;
import org.ergoplatform.appkit.*;
import org.ergoplatform.appkit.impl.BlockchainContextImpl;
import org.ergoplatform.appkit.impl.ErgoProverImpl;
import org.ergoplatform.wallet.protocol.context.ErgoLikeParameters;
import org.ergoplatform.wallet.secrets.DerivationPath;
import org.ergoplatform.wallet.secrets.ExtendedSecretKey;
import sigmastate.basics.DLogProtocol;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.*;
import java.util.stream.IntStream;

public class Main {

	private static final Path CONFIGURATION_PATH = Path.of("wallet.txt");

	public static void main(String[] args) throws Exception {
		// Load and validate everything from the configuration file
		Properties properties = new Properties();
		try {
			properties.load(new FileReader(CONFIGURATION_PATH.toFile(), StandardCharsets.UTF_8));
		} catch (FileNotFoundException e) {
			Files.copy(Main.class.getResourceAsStream("/default.txt"), CONFIGURATION_PATH);
			System.out.println("The wallet.txt configuration file was missing, it has now been created. Please fill it in and run the command again.");
			return;
		}

		byte[] encryptedKey;
		char[] password;
		int addressCount;
		Address destination;
		double donationShare;
		if (properties.getProperty("key").isBlank()) {
			System.err.println("You need to provide a key in the wallet.txt file");
			return;
		}
		try {
			encryptedKey = HexFormat.of().parseHex(properties.getProperty("key"));
		} catch (Exception e) {
			System.err.println("The key provided in the wallet.txt file is invalid");
			return;
		}
		if (properties.getProperty("password").isBlank()) {
			System.err.println("You need to provide a password in the wallet.txt file");
			return;
		}
		password = properties.getProperty("password").toCharArray();
		try {
			addressCount = Integer.parseInt(properties.getProperty("address_count"));
		} catch (NumberFormatException e) {
			System.err.println("address_count must be a number in the wallet.txt file");
			return;
		}
		try {
			destination = Address.create(properties.getProperty("destination"));
		} catch (Exception e) {
			System.err.println("The destination address in the wallet.txt file is invalid");
			return;
		}
		double tempDonationShare;
		try {
			DecimalFormat decimalFormat = new DecimalFormat("0%");
			tempDonationShare = (double) decimalFormat.parse(properties.getProperty("erg_donation_percentage"));
		} catch (ParseException e) {
			System.err.println("The donation percentage specified in wallet.txt is invalid, please specify 0% if you do not want to donate");
			return;
		} catch (NullPointerException e) {
			tempDonationShare = 0;
		}
		if (tempDonationShare > 1) {
			System.err.println("The donation percentage specified in wallet.txt is more than 100%, please fix it");
			return;
		}
		if (tempDonationShare < 0) {
			System.err.println("The donation percentage specified in wallet.txt is negative, please specify 0% if you do not want to donate");
			return;
		}
		donationShare = tempDonationShare;

		// Decrypt the wallet
		byte[] bip32KeyBytes = KeyDecryption.decrypt(encryptedKey, password);
		BIP32Data bip32Data = BIP32Data.parse(bip32KeyBytes);

		System.out.println("Wallet was decrypted successfully");

		ExtendedSecretKey master = new ExtendedSecretKey(bip32Data.keyData(), bip32Data.chainCode(), false, DerivationPath.MasterPath());
		ExtendedSecretKey parent = (ExtendedSecretKey) master.derive(DerivationPath.fromEncoded("m/44'/429'/0'/0").get());
		List<ExtendedSecretKey> secretKeys = IntStream.range(0, addressCount).mapToObj(parent::child).toList();

		// Transact the funds
		ErgoClient ergoClient = RestApiErgoClient.create(
				System.getProperty("node", "http://213.239.193.208:9053"), NetworkType.MAINNET, "",
				System.getProperty("explorerApi", "https://api.ergoplatform.com"));
		String txIdQuoted = ergoClient.execute(ctx -> {
			long feeAmount = Parameters.MinFee;

			System.out.println("Checking all addresses for funds");
			// Box things & build UnsignedTransaction
			List<InputBox> boxesToSpend = selectAllBoxes(secretKeys.stream()
					.map(secretKey -> Address.create(p2pk(secretKey.publicKey().key()).toString()))
					.toList(), ctx);
			long totalErg = boxesToSpend.stream().mapToLong(TransactionBox::getValue).sum();
			long donationErg = (long) Math.floor(totalErg * donationShare);
			// If a donation is being made, the fee will be deducted from the donation
			// Otherwise, the fee needs to be deducted from the destination ERG
			long destinationErg = donationShare > 0
					? totalErg - donationErg
					: totalErg - feeAmount;
			// Sum tokens in all boxes
			HashMap<ErgoId, Long> tokensToSend = new HashMap<>();
			for (InputBox inputBox : boxesToSpend) {
				for (ErgoToken token : inputBox.getTokens()) {
					tokensToSend.put(token.getId(), tokensToSend.getOrDefault(token.getId(), 0L) + token.getValue());
				}
			}
			ErgoToken[] tokenList = tokensToSend.entrySet().stream().map(t -> new ErgoToken(t.getKey(), t.getValue())).toArray(ErgoToken[]::new);

			System.out.println("Creating transaction");
			UnsignedTransactionBuilder txB = ctx.newTxBuilder();
			OutBoxBuilder newBoxBuilder = txB.outBoxBuilder()
					.value(destinationErg);
			if (tokenList.length > 0) {
				newBoxBuilder.tokens(tokenList);
			}
			newBoxBuilder.contract(ctx.compileContract(ConstantsBuilder.create()
					.item("recipientPk", destination.getPublicKey())
					.build(), "{ recipientPk }"));
			OutBox newBox = newBoxBuilder.build();
			UnsignedTransactionBuilder unsignedTxB = txB
					.addInputs(boxesToSpend.toArray(new InputBox[0]))
					.addOutputs(newBox);
			if (donationShare > 0) {
				unsignedTxB.addOutputs(txB.outBoxBuilder()
						.value(donationErg - feeAmount)
						.contract(ctx.compileContract(ConstantsBuilder.create()
								.item("recipientPk", Address.create("9f3tSo6Pxbs65iYbo4S1sG4vKDyTAZhDBMDt2zo7VQg2zR6X9TW").getPublicKey())
								.build(), "{ recipientPk }"))
						.build());
			}
			UnsignedTransaction unsignedTx = unsignedTxB
					.fee(feeAmount)
					.sendChangeTo(destination)
					.build();

			// Blockchain things, prover and signing
			System.out.println("Transacting all funds to the destination address" + (donationShare > 0 ? " and " + (donationShare * 100.0) + "% to the donation address" : ""));
			ErgoProverImpl proverImpl = makeProverForKeys((BlockchainContextImpl) ctx, secretKeys);
			SignedTransaction signedTx = proverImpl.sign(unsignedTx);
			return ctx.sendTransaction(signedTx);
		});
		System.out.println("SUCCESS: The transaction has been submitted, view status at https://explorer.ergoplatform.com/en/transactions/" + txIdQuoted.substring(0, txIdQuoted.length() - 1));
	}

	private static List<InputBox> selectAllBoxes(List<Address> addresses, BlockchainContext ctx) {
		BoxOperations.ExplorerApiUnspentLoader loader = new ExplorerAndPoolUnspentBoxesLoader();
		ArrayList<InputBox> inputBoxes = new ArrayList<>();
		for (Address address : addresses) {
			int page = 0;
			while (true) {
				List<InputBox> result = loader.loadBoxesPage(ctx, address, page++);
				inputBoxes.addAll(result);
				if (result.size() < BlockchainContext.DEFAULT_LIMIT_FOR_API)
					break;
			}
		}
		return inputBoxes;
	}

	private static ErgoProverImpl makeProverForKeys(BlockchainContextImpl ctx, List<ExtendedSecretKey> secretKeys) {
		BlockchainParameters par = ctx.getParameters();
		return new ErgoProverImpl(ctx, new AppkitProvingInterpreter(secretKeys, List.of(), List.of(), new ErgoLikeParameters() {
			@Override public int maxBlockSize() { return par.getMaxBlockSize(); }
			@Override public int inputCost() { return par.getInputCost(); }
			@Override public scala.Option<Object> softForkStartingHeight() { return scala.Option.empty(); }
			@Override public int storageFeeFactor() { return par.getStorageFeeFactor(); }
			@Override public int outputCost() { return par.getOutputCost(); }
			@Override public scala.Option<Object> softForkVotesCollected() { return scala.Option.empty(); }
			@Override public int tokenAccessCost() { return par.getTokenAccessCost(); }
			@Override public int maxBlockCost() { return par.getMaxBlockCost(); }
			@Override public int dataInputCost() { return par.getDataInputCost(); }
			@Override public int minValuePerByte() { return par.getMinValuePerByte(); }
			@Override public byte blockVersion() { return par.getBlockVersion(); }
		}));
	}

	private static P2PKAddress p2pk(DLogProtocol.ProveDlog proveDlog) {
		return P2PKAddress.apply(proveDlog, new ErgoAddressEncoder(ErgoAddressEncoder.MainnetNetworkPrefix()));
	}
}
