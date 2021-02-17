import java.math.BigInteger;
import java.util.Date;
import java.util.Random;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import javax.crypto.Cipher;
import sun.misc.BASE64Encoder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServiceChat extends Thread {
	final static int DATASIZE = 128; //128 to use with RSA1024_NO_PAD
	boolean debug = true;

	final static int NB_USERS_MAX = 3;
	static int nb_users = 0;
	static PrintStream[] outputs = new PrintStream[NB_USERS_MAX];
	static ArrayList<String> usernames = new ArrayList<String>();
	static ArrayList<PublicKey> rsaPubKeys = new ArrayList<PublicKey>();
	static ServiceChat[] serviceChat = new ServiceChat[NB_USERS_MAX];

	
	Socket socket;
	BufferedReader input;
	PrintStream output;
	String username;
	String Base64_exposant;
	String Base64_modulus;
	boolean isOnline = false;
	int id_user;
	boolean loop = true;
	PublicKey pubRSAkey;
	String loginRequest = "";
	byte[] challengeBytes = new byte[DATASIZE];

	public ServiceChat(Socket socket) {
		this.socket = socket;
		this.start();
	}

	public void run() {
		boolean noError = initStreams();
		if (noError)
			mainLoop();
	}

	private void mainLoop() {


		while (loop) {
			String message = getMessage();
			analyseMessage(message);
		}
	}

	private synchronized boolean initStreams() {
		try {
			input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			output = new PrintStream(socket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		if (nb_users == NB_USERS_MAX) {
			output.println("[SYSTEM] Server is full (" +nb_users+"/"+NB_USERS_MAX+") , please try again later...");
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		} else {

			if(debug)System.out.println("[DEBUG] ID = "+nb_users);
			this.id_user = nb_users;
			serviceChat[id_user] = this;
			outputs[id_user] = output;
			nb_users++;

			output.println("[SYSTEM] Welcome on chat (" +nb_users+"/"+NB_USERS_MAX+")");
			output.println("[SYSTEM] use /login <username> to connect: ");
			loginRequest = getMessage();
			//System.out.println("login request: "+loginRequest+">");
			this.username = loginRequest.split(" ")[1]; // pour garder uniquement le username
			//System.out.println("Username: <"+this.username+">");
			this.Base64_exposant = loginRequest.split(" ")[2]; // pour garder uniquement le base64 de l'exposant
			//System.out.println("Exposent: <"+this.Base64_exposant+">");
			this.Base64_modulus = loginRequest.split(" ")[3]; // pour garder uniquement le base64 du modulus
			//System.out.println("Modulus: <"+this.Base64_modulus+">");
			try {
				this.pubRSAkey = createRSAKey(this.Base64_exposant, this.Base64_modulus);
			} catch (Exception e) {
				System.out.println("[ERREUR] creation de la clef RSA");
			}
			

			if (usernameExist(this.username)) {
				output.println("[SYSTEM] Username '"+ this.username + "' was found !");	
				if (checkRSA(this.username)) {
					output.println("[SYSTEM] Successfull login !");
					broadCast("[SYSTEM] " + username + " has join the chat (" +nb_users+"/"+NB_USERS_MAX+")");
					this.isOnline = true;
					userList();
					if(debug)System.out.println("[DEBUG] Username: "+this.username+" , ID: "+this.id_user);				
					return true;
				} else {
					output.println("[SYSTEM] Credentials are not valid !");
					disconnect("error");
					return false;
				}
			} else {
				// Ici ajouter le username et sa clef public associée
				usernames.add(this.username);
				rsaPubKeys.add(this.pubRSAkey);
				output.println("[SYSTEM] Successfull login (user added) !");
				System.out.print("Username: "+this.username);
				System.out.print("RSA: "+this.pubRSAkey.toString());
				broadCast("[SYSTEM] " + username + " has join the chat (" +nb_users+"/"+NB_USERS_MAX+")");
				this.isOnline = true;
				userList();
				if(debug)System.out.println("[DEBUG] Username: "+this.username+" , ID: "+this.id_user);
			}

			return true;
		}
	}

	public synchronized void broadCast(String input) {
		for (int i = 0; i < nb_users; i++) {
			try {
				System.out.println("En sortie de serveur: <"+input+">");
				outputs[i].println(input);
			} catch (Exception e) {
				continue;
			}
		}
	}

	private String getMessage() {
		String msg = "";
		try {
			msg = input.readLine();

		} catch (IOException e) {
			output.println("[ERROR] I/O Exception");
		}
		return msg;
	}

	private void analyseMessage(String msg) {
		if(debug)System.out.println("Reception: <"+msg+">");
		String[] command = msg.split(" ");

		if(command[0].equals("/quit")){
			disconnect("");
		}
		else if(command[0].equals("/list")){
			userList();
		}
		else if(command[0].equals("MESSAGETYPE")){

			// Si MP
			if(command[1].equals("/send")){
				int userID = getUserID(command[2]);
				if(userID == -1){
					output.println("[SYSTEM] Error: The user '"+command[2]+"' does not exist or is not online !");
				}else{
					send(userID, command);
				}
			}else{ // Sinon Broadcast le MESSAGETYPE

				broadCast("MESSAGETYPE "+"<" + this.username + "> " + command[1]);
			}

		}else if(command[0].equals("FILETYPE")){
			
			if(command[1].equals("ALL")){ //Si user est "ALL" on Broadcast
				broadCastFile(command);
			}else{ // on récupère le userID et on lui envoit en MP
				int userID = getUserID(command[1]);
				if(userID == -1){
					output.println("[SYSTEM] Error: The user '"+command[1]+"' does not exist or is not online !");
				}else{
					sendFile(userID, command);
					
				}
			}
			
		}

	}

	private boolean usernameExist(String name) {
		return (usernames.contains(name));
	}

	private boolean checkRSA(String user) {
		PublicKey pubKey = null;
		for (int i = 0; i < usernames.size(); i++) {
			String username = usernames.get(i);
			if (username.equals(user)) {
				pubKey =  rsaPubKeys.get(i);
			}
		}

		String Base64cipheredChallenge = genBase64CipherChallenge(pubKey);
		
		output.println(Base64cipheredChallenge);
		// receive uncipher
		String Base64Uncipher = getMessage();
		System.out.println("Reception of Base64 uncipher is: <"+new String(Base64Uncipher)+">");
		byte[] uncipher = null;
		try {
			sun.misc.BASE64Decoder decoder = new sun.misc.BASE64Decoder();
			uncipher = decoder.decodeBuffer(Base64Uncipher);
		} catch (Exception e) {
			output.println("Erreur decodage base64");
		}

		System.out.println("Raw Challenge is: <"+new String(uncipher)+">");

		// si uncipher == challenge ---> return true
		if(uncipher.equals(this.challengeBytes)) {
			return true;
		}
		
		return false;
	}

	private String genBase64CipherChallenge(PublicKey pubKey){
		
		Random r = new Random((0));
		BASE64Encoder encoder = new BASE64Encoder();
		r.nextBytes( challengeBytes );
		System.out.println("Raw Challenge is: <"+new String(challengeBytes)+">");
		byte[] cipher = cipher(challengeBytes,pubKey);
		System.out.println("Cipher Challenge is: <"+new String(cipher)+">");
		String encodedCipher = encoder.encode(cipher);
		encodedCipher = encodedCipher.replaceAll("(?:\\r\\n|\\n\\r|\\n|\\r)", "");
		System.out.println("Base64 Cipher Challenge is: <"+new String(encodedCipher)+">");
		return encodedCipher;
	}

	private byte[] cipher(byte[] challengeBytes,PublicKey pub){
		Security.addProvider(new BouncyCastleProvider());
		byte[] ciphered =null;
		Cipher cRSA_NO_PAD = null;
		try {
			cRSA_NO_PAD = Cipher.getInstance( "RSA/NONE/NoPadding", "BC" );
			cRSA_NO_PAD.init( Cipher.ENCRYPT_MODE, pub );
			 ciphered = new byte[DATASIZE];
			System.out.println( "*" );
			cRSA_NO_PAD.doFinal(challengeBytes, 0, DATASIZE, ciphered, 0);
		} catch (Exception e) {
			System.out.println("[Error] cipher serveur side");
			e.printStackTrace();
		}
		//ciphered = cRSA_NO_PAD.doFinal( challengeBytes );
		return ciphered;
	}
	

	private void disconnect(String flag) {

		try {

			outputs[id_user] = outputs[nb_users - 1];

			if(debug)System.out.println("[DEBUG] outputs[" + id_user + "]= outputs[" + (nb_users - 1) + "]");
			if(debug)System.out.println("[DEBUG] BEFORE UPDATE, UserID: " + serviceChat[(nb_users - 1)].id_user);
			
			serviceChat[(nb_users - 1)].id_user = this.id_user;
			
			if(debug)System.out.println("[DEBUG] AFTER UPDATE, UserID: " + serviceChat[(nb_users - 1)].id_user);
			
			serviceChat[this.id_user] = serviceChat[(nb_users-1)]; //le dernier servicechat devient celui qui se déco
			
			nb_users--;
			
			if(debug)System.out.println("[DEBUG] nbUser =" + nb_users);
				
			
				if(!flag.equals("error"))
					broadCast("[SYSTEM] " + username + " has left! (" +nb_users+"/"+NB_USERS_MAX+")");

			
			this.isOnline = false;
			socket.close();
			loop = false;

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Erreur /quit");
		}
	}

	private void userList(){
			output.println("[SYSTEM] User List: ");
		for (int i=0; i<nb_users;i++) {
			if(serviceChat[i].isOnline)
				output.println("[SYSTEM] : "+serviceChat[i].username);
		}
	}

	private int getUserID(String username){
		for (int i = 0; i < nb_users; i++) {
			if(serviceChat[i].username.equals(username)){
				return i;
			}
		}
		return -1;
	}
	private void send(int userID, String[] command){
		System.out.println("En sortie de serveur: <MESSAGETYPE [Private]<" + this.username + "> "+command[3]+">");
		outputs[userID].println("MESSAGETYPE [Private]<" + this.username + "> "+command[3]);
	}

	private void sendFile(int userID,String[] command){
		outputs[userID].println(command[0]+" "+command[1]+" "+command[2]+" "+command[3]+" "+command[4]);
	}

	public synchronized void broadCastFile(String[] command) {
		for (int i = 0; i < nb_users; i++) {
			try {
				outputs[i].println(command[0]+" "+command[1]+" "+command[2]+" "+command[3]+" "+command[4]);
			} catch (Exception e) {
				continue;
			}
		}
	}


	private PublicKey createRSAKey(String base64_pub_s, String base64_mod_s) throws Exception {
		byte[] b_pub_s = null;
		byte[] b_mod_s = null;
		
		try {
			sun.misc.BASE64Decoder decoder = new sun.misc.BASE64Decoder();
			b_pub_s = decoder.decodeBuffer(base64_pub_s);
			b_mod_s = decoder.decodeBuffer(base64_mod_s);

		} catch (Exception e) {
			output.println("Erreur decodage base64");
		}

		String mod_s = new String(b_mod_s);
		String pub_s = new String(b_pub_s);
		//System.out.println("Mod: <"+mod_s+">");
		//System.out.println("Pub: <"+pub_s+">");

		// Load the keys from String into BigIntegers (step 3)
		BigInteger modulus = new BigInteger(mod_s, 16);
		BigInteger pubExponent = new BigInteger(pub_s, 16);

		// Create public key specs from BinIntegers (step 4)
		RSAPublicKeySpec publicSpec = new RSAPublicKeySpec(modulus, pubExponent);

		// Create the RSA  public keys (step 5)
		KeyFactory factory = KeyFactory.getInstance( "RSA" );
		PublicKey pub = factory.generatePublic(publicSpec);

		return pub;
	}


}