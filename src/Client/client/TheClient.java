package client;

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
import opencard.core.service.CardRequest;
import opencard.core.service.SmartCard;
import opencard.core.terminal.CommandAPDU;
import opencard.core.terminal.ResponseAPDU;
import opencard.core.terminal.APDU;
import opencard.core.util.HexString;
import opencard.opt.util.PassThruCardService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import sun.misc.BASE64Encoder;

import java.io.*;
import java.net.*;
import java.util.*;





public class TheClient extends Thread{



	private final static byte CLA_TEST                    		= (byte)0x90;
	private final static byte INS_DES_ECB_NOPAD_ENC           	= (byte)0x20;
	private final static byte INS_DES_ECB_NOPAD_DEC           	= (byte)0x21;
	private final static byte INS_RSA_ENC		           	= (byte)0x00;
	private final static byte INS_RSA_DEC		           	= (byte)0x01;
	private final static byte INS_RSA_ENCRYPT             = (byte)0xA0;
	private final static byte INS_RSA_DECRYPT             = (byte)0xA2;

	private final static byte INS_GET_PUBLIC_RSA_KEY      = (byte)0xFE;
	private PassThruCardService servClient = null;
	final static boolean DISPLAYAPDUS = true;
	boolean DISPLAY = false;

	// ----------------------------------------------------------

	boolean debug = false;
	boolean loop = true;
	boolean isLogged = false;
	Socket socket;

	BufferedReader input_client;
	PrintStream output_client;

	BufferedReader input_server;
	PrintStream output_server;

	String randomStrfilename = "";

	String mod_s = "";
	String pub_s ="";
	PublicKey pubRSAkey;
	

	final static short CIPHER_MAXLENGTH = 240;

	//------------------------------------------------------------------------------------
	//----------------------------Main et Constructeur------------------------------------

	public static void main( String[] args ) throws InterruptedException {
		String host = "localhost";
		int port = 1234;
		try{
			Socket socket = new Socket(host,port);
			new TheClient(socket);
		}catch(Exception e){
			System.out.println( "Error: unable to join "+host+":"+port);
		}
		
	}



	public TheClient(Socket socket) {
		try {
			SmartCard.start();
			System.out.print( "Smartcard inserted?... " ); 
			CardRequest cr = new CardRequest (CardRequest.ANYCARD,null,null); 
			SmartCard sm = SmartCard.waitForCard (cr);
			if (sm != null) {
				System.out.println ("got a SmartCard object!\n");
			} else
				System.out.println( "did not get a SmartCard object!\n" );
			initNewCard( sm );
			//SmartCard.shutdown(); 
			
		} catch( Exception e ) {
			System.out.println( "TheClient error: " + e.getMessage() );
		}


		this.socket = socket;
		boolean noError = initStreams();
		

		
		if (noError){
			
			try {
				this.pub_s = initExposant();
				this.mod_s = initModulus();
				this.pubRSAkey = createRSAKey(this.pub_s,this.mod_s);
				
			} catch( Exception e ) {
			System.out.println( "Erreur lors de la conversion des exposants: " + e );
			}
			this.start();
			read();
		}

	}

	public void run() {
		listen();
	}

	//------------------------------------------------------------------------------------
	//------------------------------------------------------------------------------------
	private ResponseAPDU sendAPDU(CommandAPDU cmd) {
		return sendAPDU(cmd, DISPLAYAPDUS);
	}


	private ResponseAPDU sendAPDU( CommandAPDU cmd, boolean display ) {
		ResponseAPDU result = null;
		try {
			result = servClient.sendCommandAPDU( cmd );
			if(display)
				displayAPDU(cmd, result);
		} catch( Exception e ) {
			System.out.println( "Exception caught in sendAPDU: " + e.getMessage() );
			java.lang.System.exit( -1 );
		}
		return result;
	}


	/************************************************
	 * *********** BEGINNING TOOLS ***************
	 * **********************************************/


	private String apdu2string( APDU apdu ) {
		return removeCR( HexString.hexify( apdu.getBytes() ) );
	}


	public void displayAPDU( APDU apdu ) {
		System.out.println( removeCR( HexString.hexify( apdu.getBytes() ) ) + "\n" );
	}


	public void displayAPDU( CommandAPDU termCmd, ResponseAPDU cardResp ) {
		System.out.println( "--> Term: " + removeCR( HexString.hexify( termCmd.getBytes() ) ) );
		System.out.println( "<-- Card: " + removeCR( HexString.hexify( cardResp.getBytes() ) ) );
	}


	private String removeCR( String string ) {
		return string.replace( '\n', ' ' );
	}


	/******************************************
	 * *********** ENDING TOOLS ***************
	 * ****************************************/


	private boolean selectApplet() {
		boolean cardOk = false;
		try {
			CommandAPDU cmd = new CommandAPDU( new byte[] {
				(byte)0x00, (byte)0xA4, (byte)0x04, (byte)0x00, (byte)0x0A,
				    (byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x62, 
				    (byte)0x03, (byte)0x01, (byte)0x0C, (byte)0x06, (byte)0x01
			} );
			ResponseAPDU resp = sendAPDU( cmd );
			if( apdu2string( resp ).equals( "90 00" ) )
				cardOk = true;
		} catch(Exception e) {
			System.out.println( "Exception caught in selectApplet: " + e.getMessage() );
			java.lang.System.exit( -1 );
		}
		return cardOk;
	}


	private void initNewCard( SmartCard card ) {
		if( card != null )
			System.out.println( "Smartcard inserted\n" );
		else {
			System.out.println( "Did not get a smartcard" );
			System.exit( -1 );
		}

		System.out.println( "ATR: " + HexString.hexify( card.getCardID().getATR() ) + "\n");


		try {
			servClient = (PassThruCardService)card.getCardService( PassThruCardService.class, true );
		} catch( Exception e ) {
			System.out.println( e.getMessage() );
		}

		System.out.println("Applet selecting...");
		if( !selectApplet() ) {
			System.out.println( "Wrong card, no applet to select!\n" );
			System.exit( 1 );
			return;
		} else 
			System.out.println( "Applet selected\n" );

			// try {
			// 	mainContent();
			// } catch( Exception e ) {
			// 	System.out.println( "initNewCard: " + e );
			// }
	}


	/************************************************/

	private String initModulus() throws Exception{
		// Get keys binary (byte[]) content (step 1)
		byte[] modulus_b = getModulus();
		// Transform byte[] into String (step 2)
		String mod_s =  HexString.hexify( modulus_b );
		mod_s = mod_s.replaceAll( " ", "" );
		mod_s = mod_s.replaceAll( "\n", "" );
		return mod_s;
	}

	private String initExposant() throws Exception{
		// Get keys binary (byte[]) content (step 1)
		byte[] public_exponent_b = getPublicExponent();
		// Transform byte[] into String (step 2)
		String pub_s =  HexString.hexify( public_exponent_b );
		pub_s = pub_s.replaceAll( " ", "" );
		pub_s = pub_s.replaceAll( "\n", "" );
		return pub_s;
	}
	private PublicKey createRSAKey(String pub_s, String mod_s) throws Exception {

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

	private byte[] cipherGeneric( byte typeINS, byte[] challenge ) {
		byte[] result = new byte[challenge.length];

		/* Forgage de la requete pour cippher/uncipher*/

		byte[] header = {CLA_TEST,typeINS, 0x00,0x00};

		byte[] optional = new byte[(2+challenge.length)];
		optional[0] = (byte)challenge.length;
		System.arraycopy(challenge, 0, optional, (byte)1,(short)((short)optional[0]&(short)255));
		byte[] command = new byte[header.length + optional.length];
		System.arraycopy(header, (byte)0, command, (byte)0, header.length);
		System.arraycopy(optional, (byte)0, command,header.length, optional.length);

		CommandAPDU cmd = new CommandAPDU( command);
		//	displayAPDU(cmd);

		/*end Requete*/

		/* Reception et retour du cipher */
		ResponseAPDU resp = this.sendAPDU( cmd, DISPLAY );
		byte[] bytes = resp.getBytes();
		System.arraycopy(bytes, 0, result, 0, (bytes.length-2));
		return result;		
    }



	//---------------------------------------------------------------------------------------------------
	//------------------------- Partie Client "pure"-----------------------------------------------------

	private byte[] getModulus(){
		
		byte[] apdu = {CLA_TEST,INS_GET_PUBLIC_RSA_KEY,0x00,0x00,0x00};
		CommandAPDU cmd = new CommandAPDU( apdu );
		ResponseAPDU resp = sendAPDU( cmd, DISPLAY );
		byte[] response = resp.getBytes();
		byte[] modulus = new byte[response.length-3];
		System.arraycopy(response, 1, modulus, 0,(response.length-3));
		return modulus;
	}
	
	private byte[] getPublicExponent(){
		byte[] apdu = {CLA_TEST,INS_GET_PUBLIC_RSA_KEY,0x00,0x01,0x00};
		CommandAPDU cmd = new CommandAPDU( apdu );
		ResponseAPDU resp = sendAPDU( cmd, DISPLAY );
		byte[] response = resp.getBytes();
		byte[] exponent = new byte[response.length-3];
		System.arraycopy(response, 1, exponent, 0,(response.length-3));
		return exponent;
	}

	private void loginRequest(String request){
		sun.misc.BASE64Encoder encoder = new sun.misc.BASE64Encoder();
		String encodedModulus = encoder.encode(this.mod_s.getBytes());
		encodedModulus = encodedModulus.replaceAll("(?:\\r\\n|\\n\\r|\\n|\\r)", "");
		String encodedExponent = encoder.encode(this.pub_s.getBytes());
		encodedExponent = encodedExponent.replaceAll("(?:\\r\\n|\\n\\r|\\n|\\r)", "");
		String messageTransform = request + " " + encodedExponent + " " + encodedModulus;
		send(messageTransform);

		String Base_64cipheredChallenge = getMessage(input_server);
		if(Base_64cipheredChallenge.startsWith("[SYSTEM]")){ // si première connexion
			output_client.println("[CLIENT] Registered !");
		}else{ // si connexion ---> challengeProcess

			//output_client.println("Challenge: <"+Base_64cipheredChallenge+">");
			byte[] cipheredChallenge = null;
			byte[] unciphered = null;
			try {
				sun.misc.BASE64Decoder decoder = new sun.misc.BASE64Decoder();
				cipheredChallenge = decoder.decodeBuffer(Base_64cipheredChallenge);
			} catch (Exception e) {
				output_client.println("Erreur decodage du Base_64cipheredChallenge");
			}
			//output_client.println("Decodage challenge (now chiffre): <"+new String(cipheredChallenge)+">");
			
			// envoit du cipher vers la CaP (et reception du unciphered)
			unciphered = cipherGeneric(INS_RSA_DECRYPT, cipheredChallenge);
			//output_client.println("Uncipher challenge: <"+new String(unciphered)+">");
			//envoit du unciphered
			String encodedUnciphered = encoder.encode(unciphered);
			encodedUnciphered = encodedUnciphered.replaceAll("(?:\\r\\n|\\n\\r|\\n|\\r)", "");
			//output_client.println("Base64 challenge is: <"+new String(encodedUnciphered)+">");
			send(encodedUnciphered);
		}
		
	}

	// NB: cote client, on recupere modulus et exposant de la carte;
	// On send au serveur le modulus et l'exposant au serveur ---> lui permet de creer l'objet RSApubKey 
	// le serveur genere un challenge qu'il peut chiffrer avec la pubKey
	// cote client on peut Decrypt avec l'apdu , puis renvoyer au serveur le déchiffrer
	// ---> match on est login en tant que le user et on stock le couple <user/PubKey>

	private void help(){
		output_client.println("========== Help ==========");
		output_client.println("Send private message : /send <user> <message>");
		output_client.println("Send private file : /file <user> <filename>");
		output_client.println("Broadcast file : /file ALL <filename>");
		output_client.println("Broadcast message : <message>");
		output_client.println("Display user list : /list");
		output_client.println("Display help : /help");
		output_client.println("Disconnect : /quit");
		output_client.println("==========================");
	}

	private void listen() { // ecoute ce qui arrive du serveur
		while (loop) {
			String message = getMessage(input_server);

			receive(message);

			try {
				if(message.startsWith("[SYSTEM] Server is full")){
					System.exit(0);
				}else if(message.startsWith("[SYSTEM] Credentials are not valid !")){
					System.exit(0);
				}
			} catch (Exception e) {
				output_client.println("Catch disconnect...");
			}

			if(message.startsWith("[SYSTEM] Successfull login")){
				this.isLogged = true;
			}

			}
	}

	private void read() { // lit ce qui est saisi par le user
		
		while (loop) {
					String message = getMessage(input_client);
					String messageTransform = "";
					
					if(!isLogged){

							if(message.startsWith("/login ")){ 

							StringTokenizer st = new StringTokenizer(message);
							if(st.countTokens() == 2){
									loginRequest(message);
							}else{
								output_client.println("Error: Wrong number of argument, command is: /login <username>");
							}

						}else if(message.equals("/quit")){
							send(message);
							output_client.println("Exiting system...");
							try {
								SmartCard.shutdown();
							} catch (Exception e) {
								output_client.println("Exiting SmartCard...");
							}
							System.exit(0);

						}else if(message.equals("/help")){
							help();
						}else{
							output_client.println("You should log in first with : /login <username> !");
						}

					}else{	// Si user est log

						if(message.equals("/quit")){
							send(message);
							output_client.println("Exiting system...");
							try {
								SmartCard.shutdown();
							} catch (Exception e) {
								output_client.println("Exiting SmartCard...");
							}
							System.exit(0);

						}else if(message.equals("/help")){
							help();
						}else if(message.equals("/list")){
							send(message);
							output_client.println("Asking for online users list...");
	
						}else if(message.startsWith("/file ")){
	
								DataInputStream file = null;
								Boolean fileExist = false;
								
								StringTokenizer st = new StringTokenizer(message);
								if(st.countTokens() == 3){
									String user = "";
									String inputfilename = "";
									for(int i=0;i<2;i++){
										user = st.nextToken();
									}
									inputfilename = st.nextToken();
									
									
									try{
										file = new DataInputStream(new FileInputStream(inputfilename));
										fileExist = true;
										output_client.println("Sending "+inputfilename+" to "+user+" ...");
									}catch(Exception e){
										output_client.println("Error: error file does not exist");
									}
	
	
									if(fileExist){
										try{
										
											int return_value = 0;
											int blockNumber = 0;
											byte[] cipherBlock;
											byte[] blockFileDataToSend = new byte[CIPHER_MAXLENGTH];
	
											while( (return_value = file.read(blockFileDataToSend)) !=-1 ) {
	
												if(return_value == CIPHER_MAXLENGTH){
													cipherBlock = cipherGeneric(INS_DES_ECB_NOPAD_ENC, blockFileDataToSend);
													
												}else{
							
													 int paddingSize = (8-(return_value%8));
													 byte[] finalData = new byte[return_value+paddingSize];
													 
													 byte[] finalPadding = new byte[paddingSize];
													 for(int i =0; i < paddingSize ; i++){
													 finalPadding[i]= (byte)(paddingSize+48); //(+48 pour offset dans la table ASCII)
													 }
							
													 System.arraycopy(blockFileDataToSend, (byte)0, finalData, (byte)0, return_value);
													 System.arraycopy(finalPadding, (byte)0, finalData,return_value,paddingSize);
													//nb FinalData est mon bloc paddé non chiffré
													
													cipherBlock = cipherGeneric(INS_DES_ECB_NOPAD_ENC, finalData);
												}
												
												blockNumber ++;
												// send le bloc paddé chiffré ici (cipherBlock)
					
												sun.misc.BASE64Encoder encoder = new sun.misc.BASE64Encoder();
												String encodedString = encoder.encode(cipherBlock);
												encodedString = encodedString.replaceAll("(?:\\r\\n|\\n\\r|\\n|\\r)", "");
												messageTransform = "FILETYPE "+user+" "+blockNumber+" "+inputfilename+" "+encodedString;
												send(messageTransform);
												
											}
											file.close();
							
										}catch(Exception e){
											output_client.println("Error: while reading file (for sending)");
											//e.printStackTrace();
										}
	
									}
	
								
							}else{
								output_client.println("Error: Wrong number of argument, command is:");
								output_client.println("/file <user> <file>");
								output_client.println("/file ALL <file>");
							}	
						}else if(message.startsWith("/send")){
										StringTokenizer st = new StringTokenizer(message);
										if(st.countTokens() >= 3){
											String user = "";
											String content = "";
											for(int i=0;i<2;i++){
												user = st.nextToken();
											}
											while(st.hasMoreTokens()){
											content+=st.nextToken();
											content+=" ";
										}
										content = content.trim();
	
	
										//cipher here 
										int return_value = 0;
										byte[] msgCipherBlock;
										byte[] msgDataBlock = new byte[CIPHER_MAXLENGTH];
										return_value = content.length();
	
										if(return_value == CIPHER_MAXLENGTH){
											msgDataBlock = content.getBytes();
											msgCipherBlock = cipherGeneric(INS_DES_ECB_NOPAD_ENC, msgDataBlock);					
											}else{
												msgDataBlock = content.getBytes();
											int paddingSize = (8-(return_value%8));
											byte[] finalData = new byte[return_value+paddingSize];
											
											byte[] finalPadding = new byte[paddingSize];
											for(int i =0; i < paddingSize ; i++){
											finalPadding[i]= (byte)(paddingSize+48); //(+48 pour offset dans la table ASCII)
											}
	
											System.arraycopy(msgDataBlock, (byte)0, finalData, (byte)0, return_value);
											System.arraycopy(finalPadding, (byte)0, finalData,return_value,paddingSize);
											//nb FinalData est mon bloc paddé non chiffré
											
											msgCipherBlock = cipherGeneric(INS_DES_ECB_NOPAD_ENC, finalData);
										}
	
										sun.misc.BASE64Encoder encoder = new sun.misc.BASE64Encoder();
										String encodedString = encoder.encode(msgCipherBlock);
										encodedString = encodedString.replaceAll("(?:\\r\\n|\\n\\r|\\n|\\r)", "");
	
											messageTransform = "MESSAGETYPE /send "+user+" "+encodedString;
											send(messageTransform);
									}else{
										output_client.println("Error: Wrong number of argument, command is: /send <user> <message>");
									}
	
							}else{// si Broadcast d'un MESSAGETYPE
	
								StringTokenizer st = new StringTokenizer(message);
								String content = "";
								while(st.hasMoreTokens()){
									content+=st.nextToken();
									content+=" ";
								}
								content = content.trim();
	
							int return_value = 0;
							byte[] msgCipherBlock;
							byte[] msgDataBlock = new byte[CIPHER_MAXLENGTH];
	
	
							return_value = content.length();
	
							if(return_value == CIPHER_MAXLENGTH){
								msgDataBlock = content.getBytes();
								msgCipherBlock = cipherGeneric(INS_DES_ECB_NOPAD_ENC, msgDataBlock);					
							}else{
								msgDataBlock = content.getBytes();
								int paddingSize = (8-(return_value%8));
								byte[] finalData = new byte[return_value+paddingSize];
								
								byte[] finalPadding = new byte[paddingSize];
								for(int i =0; i < paddingSize ; i++){
								finalPadding[i]= (byte)(paddingSize+48); //(+48 pour offset dans la table ASCII)
								}
	
								System.arraycopy(msgDataBlock, (byte)0, finalData, (byte)0, return_value);
								System.arraycopy(finalPadding, (byte)0, finalData,return_value,paddingSize);
								//nb FinalData est mon bloc paddé non chiffré
								
								msgCipherBlock = cipherGeneric(INS_DES_ECB_NOPAD_ENC, finalData);
								//cipherBlock = finalData;
							}
	
							sun.misc.BASE64Encoder encoder = new sun.misc.BASE64Encoder();
							String encodedString = encoder.encode(msgCipherBlock);
							encodedString = encodedString.replaceAll("(?:\\r\\n|\\n\\r|\\n|\\r)", "");
	
							messageTransform = "MESSAGETYPE "+encodedString;
							send(messageTransform);
							//output_client.println(messageTransform);
							}
					}

					
					
					
					
			}
	}


	private synchronized boolean initStreams() {
		try {
			input_client = new BufferedReader(new InputStreamReader(System.in));
			output_client = new PrintStream(System.out);
			input_server = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			output_server = new PrintStream(socket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error init Stream");
			return false;
		}
		return true;
	}


	private String getMessage(BufferedReader buffer) {
		String msg = "";
		try {
			msg = buffer.readLine();

		} catch (IOException e) {
			output_client.println("Closing connexion...");
			System.exit(0);
		}
		return msg;
	}

	private String unpadMessage(String message){
		int padding_extrait = (message.charAt(message.length()-1)-48); //(-48 pour offset dans la table ASCII)
		String unpadStr = message.substring(0, message.length()-padding_extrait);
		return unpadStr;
	}

	private void send(String message){
		try {
			output_server.println(message);
		} catch (Exception e) {
			output_client.println("[ERROR] send()");
		}
	}

	private void receive(String message){
		String[] command = null;
		try {
			command = message.split(" ");
		} catch (Exception e) {
			output_client.println("Closing connexion...");
			System.exit(0);
		}
		 
		String toDisplay ="";
		

		if(command[0].equals("MESSAGETYPE")){

				toDisplay = command[2];

				//uncipher here 
				byte[] msgData = null;
				int return_value = 0;
				byte[] uncipherBlock;
				
				try {
					sun.misc.BASE64Decoder decoder = new sun.misc.BASE64Decoder();
					msgData = decoder.decodeBuffer(toDisplay);
				} catch (Exception e) {
					output_client.println("Erreur decodage base64 lors de l'uncipher");
				}

				return_value = msgData.length;

			if(return_value == CIPHER_MAXLENGTH){
				uncipherBlock = cipherGeneric(INS_DES_ECB_NOPAD_DEC, msgData);
				String outputStr = new String(uncipherBlock);
				String unpadOutput = unpadMessage(outputStr);
				output_client.println(command[1]+unpadOutput);		
			}else{
							// extration du bon bout
							byte[] finalData = new byte[return_value];
							System.arraycopy(msgData, (byte)0, finalData, (byte)0, return_value);
							// uncipher
							uncipherBlock = cipherGeneric(INS_DES_ECB_NOPAD_DEC, finalData);
							// retirer padding
							int padding_extrait = (uncipherBlock[return_value-1]-48); //(-48 pour offset dans la table ASCII)
							String outputStr = new String(uncipherBlock);
							//NB: reste a retirer le padding
							String unpadOutput = unpadMessage(outputStr);
							output_client.println(command[1]+unpadOutput);
						

			}


		}else if(command[0].equals("FILETYPE")){

			sun.misc.BASE64Decoder decoder = new sun.misc.BASE64Decoder();
			
			String sender = command[1];
			int blockNumber = Integer.parseInt(command[2]);
			String filename = command[3];
			String stringFileData = command[4];
			byte[] receptionFileDataBlock = null;
			DataOutputStream receivedFile;
			
			byte[] response;
			int return_value = 0;
			Boolean decode = false;

			try {
				receptionFileDataBlock = decoder.decodeBuffer(stringFileData);
				decode = true;
			} catch (Exception e) {
				output_client.println("Erreur decodage base64");
			}
			
			

			if(blockNumber == 1){

					Random r = new Random((0));
					byte[] random = new byte[32];
					r.nextBytes( random );
					
					randomStrfilename = random.toString();
			}

					try{
						return_value = receptionFileDataBlock.length;

	
						if(return_value == CIPHER_MAXLENGTH){
							response = cipherGeneric(INS_DES_ECB_NOPAD_DEC, receptionFileDataBlock);
							//response = receptionDataBlock;
							receivedFile = new DataOutputStream(new FileOutputStream(randomStrfilename+"_"+filename,true)); // true pour append
							receivedFile.write(response, 0, return_value);
							receivedFile.close();
						}else{
							// extration du bon bout
							byte[] finalData = new byte[return_value];
							System.arraycopy(receptionFileDataBlock, (byte)0, finalData, (byte)0, return_value);
							// uncipher
							response = cipherGeneric(INS_DES_ECB_NOPAD_DEC, finalData);
							//response = receptionDataBlock;
							// retirer padding
							int padding_extrait = (response[return_value-1]-48); //(-48 pour offset dans la table ASCII)
							receivedFile = new DataOutputStream(new FileOutputStream(randomStrfilename+"_"+filename,true)); // true pour append
							receivedFile.write(response, 0, return_value-padding_extrait);
							receivedFile.close();						
						}
						output_client.println("Receiving Block "+blockNumber+" of file: "+filename);				
	
				}catch(Exception e){
					output_client.println("Erreur lors de la reception d'un block de fichier");
				}
				
			
		}else if(command[0].equals("[SYSTEM]")){
			// si system, on display tel quel
			String systemStr = "";
			for (int i = 0; i < command.length; i++) {
				systemStr+=command[i];
				systemStr+=" ";
			}
			systemStr = systemStr.trim();
			output_client.println(systemStr);

		}


	}


}
