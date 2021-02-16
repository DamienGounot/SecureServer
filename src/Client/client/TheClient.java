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
	boolean DISPLAY = true;

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

	private byte[] processingDES( byte typeINS, byte[] challenge ) {
		byte[] result = new byte[challenge.length];
		byte[] headers = { CLA_TEST, typeINS, 0, 0 };
		byte[] apdu = new byte[5+challenge.length+1];

		System.arraycopy( headers, 0, apdu, 0, headers.length );
		apdu[4] = (byte)challenge.length;
		System.arraycopy( challenge, 0, apdu, 5, challenge.length );
		apdu[apdu.length-1] = (byte)challenge.length;

		CommandAPDU cmd = new CommandAPDU( apdu );
		ResponseAPDU resp = sendAPDU( cmd, false );
		byte[] response = resp.getBytes();
		System.arraycopy( response, 0, result, 0, result.length );
		return result;
	}


	private byte[] crypt( byte[] challenge ) {
		return processingDES( INS_RSA_ENC, challenge );
	} 


	private byte[] uncrypt( byte[] challenge ) {
		return processingDES( INS_RSA_DEC, challenge );
	} 


	private void mainContent() throws Exception {
		// How to hardcode the RSA keys from byte[] to PublicKey and PrivateKey objects: 5 steps

		// Get keys binary (byte[]) content (step 1)
		byte[] modulus_b = new byte[] {
			(byte)0x90,(byte)0x08,(byte)0x15,(byte)0x32,(byte)0xb3,(byte)0x6a,(byte)0x20,(byte)0x2f,
			(byte)0x40,(byte)0xa7,(byte)0xe8,(byte)0x02,(byte)0xac,(byte)0x5d,(byte)0xec,(byte)0x11,
			(byte)0x1d,(byte)0xfa,(byte)0xf0,(byte)0x6b,(byte)0x1c,(byte)0xb7,(byte)0xa8,(byte)0x39,
			(byte)0x19,(byte)0x50,(byte)0x9c,(byte)0x44,(byte)0xed,(byte)0xa9,(byte)0x51,(byte)0x01,
			(byte)0x0f,(byte)0x11,(byte)0xd6,(byte)0xa3,(byte)0x60,(byte)0xa7,(byte)0x7e,(byte)0x95,
			(byte)0xa2,(byte)0xfa,(byte)0xe0,(byte)0x8d,(byte)0x62,(byte)0x5b,(byte)0xf2,(byte)0x62,
			(byte)0xa2,(byte)0x64,(byte)0xfb,(byte)0x39,(byte)0xb0,(byte)0xf0,(byte)0x6f,(byte)0xa2,
			(byte)0x23,(byte)0xae,(byte)0xbc,(byte)0x5d,(byte)0xd0,(byte)0x1a,(byte)0x68,(byte)0x11,
			(byte)0xa7,(byte)0xc7,(byte)0x1b,(byte)0xda,(byte)0x17,(byte)0xc7,(byte)0x14,(byte)0xab,
			(byte)0x25,(byte)0x92,(byte)0xbf,(byte)0xcc,(byte)0x81,(byte)0x65,(byte)0x7a,(byte)0x08,
			(byte)0x90,(byte)0x59,(byte)0x7f,(byte)0xc4,(byte)0xf9,(byte)0x43,(byte)0x9c,(byte)0xaa,
			(byte)0xbe,(byte)0xe4,(byte)0xf8,(byte)0xfb,(byte)0x03,(byte)0x74,(byte)0x3d,(byte)0xfb,
			(byte)0x59,(byte)0x7a,(byte)0x56,(byte)0xa3,(byte)0x19,(byte)0x66,(byte)0x43,(byte)0x77,
			(byte)0xcc,(byte)0x5a,(byte)0xae,(byte)0x21,(byte)0xf5,(byte)0x20,(byte)0xa1,(byte)0x22,
			(byte)0x8f,(byte)0x3c,(byte)0xdf,(byte)0xd2,(byte)0x03,(byte)0xe9,(byte)0xc2,(byte)0x38,
			(byte)0xe7,(byte)0xd9,(byte)0x38,(byte)0xef,(byte)0x35,(byte)0x82,(byte)0x48,(byte)0xb7
		};
		byte[] private_exponent_b = new byte[] {
			(byte)0x69,(byte)0xdf,(byte)0x67,(byte)0x25,(byte)0xa3,(byte)0xb8,(byte)0x88,(byte)0xfb,
			(byte)0xf2,(byte)0xfc,(byte)0xf9,(byte)0x90,(byte)0xad,(byte)0x7f,(byte)0x44,(byte)0xbd,
			(byte)0xb8,(byte)0x59,(byte)0xf3,(byte)0x4b,(byte)0xe9,(byte)0x0a,(byte)0x1f,(byte)0x80,
			(byte)0x09,(byte)0x59,(byte)0xb5,(byte)0xe4,(byte)0xfd,(byte)0x06,(byte)0x0e,(byte)0xe3,
			(byte)0x46,(byte)0x5e,(byte)0x88,(byte)0x76,(byte)0x03,(byte)0xe0,(byte)0x5b,(byte)0x2e,
			(byte)0x47,(byte)0x65,(byte)0x3e,(byte)0x96,(byte)0xef,(byte)0x0c,(byte)0x43,(byte)0x79,
			(byte)0xb9,(byte)0x81,(byte)0x9d,(byte)0x21,(byte)0xe5,(byte)0x2c,(byte)0x78,(byte)0x02,
			(byte)0xa9,(byte)0x54,(byte)0x12,(byte)0x66,(byte)0xab,(byte)0x48,(byte)0x1d,(byte)0xe2,
			(byte)0x6e,(byte)0x1d,(byte)0x7d,(byte)0xb2,(byte)0xce,(byte)0x7a,(byte)0x3f,(byte)0xbb,
			(byte)0x34,(byte)0xf2,(byte)0x46,(byte)0x5f,(byte)0x73,(byte)0x7c,(byte)0xba,(byte)0xf8,
			(byte)0xc1,(byte)0x29,(byte)0x97,(byte)0x85,(byte)0x67,(byte)0xdf,(byte)0x82,(byte)0x87,
			(byte)0x89,(byte)0x61,(byte)0x42,(byte)0xcc,(byte)0x1d,(byte)0xcc,(byte)0x03,(byte)0xce,
			(byte)0x41,(byte)0x7d,(byte)0x8f,(byte)0x25,(byte)0xc1,(byte)0x61,(byte)0xfe,(byte)0x06,
			(byte)0x4f,(byte)0x1a,(byte)0xf2,(byte)0x48,(byte)0x55,(byte)0xd8,(byte)0x6e,(byte)0xc6,
			(byte)0x3f,(byte)0x6d,(byte)0xe1,(byte)0xce,(byte)0xa9,(byte)0x28,(byte)0x9e,(byte)0x03,
			(byte)0x2d,(byte)0x74,(byte)0x59,(byte)0x1c,(byte)0xdb,(byte)0x18,(byte)0xb3,(byte)0x41
		};
		byte[] public_exponent_b = new byte[] { (byte)0x01,(byte)0x00,(byte)0x01 };

		// Transform byte[] into String (step 2)
		String mod_s =  HexString.hexify( modulus_b );
		mod_s = mod_s.replaceAll( " ", "" );
		mod_s = mod_s.replaceAll( "\n", "" );

		String pub_s =  HexString.hexify( public_exponent_b );
		pub_s = pub_s.replaceAll( " ", "" );
		pub_s = pub_s.replaceAll( "\n", "" );

		String priv_s =  HexString.hexify( private_exponent_b );
		priv_s = priv_s.replaceAll( " ", "" );
		priv_s = priv_s.replaceAll( "\n", "" );

		// Load the keys from String into BigIntegers (step 3)
		BigInteger modulus = new BigInteger(mod_s, 16);
		BigInteger pubExponent = new BigInteger(pub_s, 16);
		BigInteger privExponent = new BigInteger(priv_s, 16);

		// Create private and public key specs from BinIntegers (step 4)
		RSAPublicKeySpec publicSpec = new RSAPublicKeySpec(modulus, pubExponent);
		RSAPrivateKeySpec privateSpec = new RSAPrivateKeySpec(modulus, privExponent);

		// Create the RSA private and public keys (step 5)
		KeyFactory factory = KeyFactory.getInstance( "RSA" );
		PublicKey pub = factory.generatePublic(publicSpec);
		PrivateKey priv = factory.generatePrivate(privateSpec);


		// How to crypt and uncrypt using RSA_NOPAD: 4 Steps

		// Get Cipher able to apply RSA_NOPAD (step 1)
		// (must use "Bouncy Castle" crypto provider)
		Security.addProvider(new BouncyCastleProvider());
		Cipher cRSA_NO_PAD = Cipher.getInstance( "RSA/NONE/NoPadding", "BC" );

		// Get challenge data (step 2)
		final int DATASIZE = 128;				//128 to use with RSA1024_NO_PAD
		//Random r = new Random( (new Date()).getTime() );
		Random r = new Random((0));
		BASE64Encoder encoder = new BASE64Encoder();
		byte[] challengeBytes = new byte[DATASIZE];
		r.nextBytes( challengeBytes );
		System.out.println("challenge:\n" + encoder.encode( challengeBytes ) + "\n" );
		
		// Crypt with public key (step 3)
		cRSA_NO_PAD.init( Cipher.ENCRYPT_MODE, pub );
		byte[] ciphered = new byte[DATASIZE];
		System.out.println( "*" );
		cRSA_NO_PAD.doFinal(challengeBytes, 0, DATASIZE, ciphered, 0);
		//ciphered = cRSA_NO_PAD.doFinal( challengeBytes );
		System.out.println( "*" );
		System.out.println("ciphered by pc is:\n" + encoder.encode(ciphered) + "\n" );


		// envoit du cipher vers la CaP (et reception du unciphered)
		byte[] unciphered;
		unciphered = cipherGeneric(INS_RSA_DECRYPT, ciphered);
		System.out.println("unciphered by card is:\n" + encoder.encode(unciphered) + "\n" );

		/*
		// Decrypt with private key (step 4)
		cRSA_NO_PAD.init( Cipher.DECRYPT_MODE, priv );
		byte[] unciphered = new byte[DATASIZE];
		cRSA_NO_PAD.doFinal( ciphered, 0, DATASIZE, unciphered, 0);
		System.out.println("unciphered by pc is:\n" + encoder.encode(unciphered) + "\n" );
		*/
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
		ResponseAPDU resp = sendAPDU( cmd, false );
		byte[] modulus = resp.getBytes();
		return modulus;
	}
	
	private byte[] getExponent(){
		byte[] apdu = {CLA_TEST,INS_GET_PUBLIC_RSA_KEY,0x00,0x01,0x00};
		CommandAPDU cmd = new CommandAPDU( apdu );
		ResponseAPDU resp = sendAPDU( cmd, false );
		byte[] exponent = resp.getBytes();
		return exponent;
	}

	// NB: cote client, on recupere modulus et exposant de la carte, puis on peut creer notre obj RSAPrivate;
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
							if(st.countTokens() == 3){
									send(message);
							}else{
								output_client.println("Error: Wrong number of argument, command is: /login <username> <password>");
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
							output_client.println("You should log in first !");
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
			output_client.println("[ERROR] getMessage()");
		}
		return msg;
	}

	private void send(String message){
		try {
			output_server.println(message);
		} catch (Exception e) {
			output_client.println("[ERROR] send()");
		}
	}

	private void receive(String message){
		String[] command = message.split(" ");
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
				output_client.println(command[1]+outputStr);		
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
							output_client.println(command[1]+outputStr);
						

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
