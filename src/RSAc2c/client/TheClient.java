package client;

import java.io.*;
import opencard.core.service.*;
import opencard.core.terminal.*;
import opencard.core.util.*;
import opencard.opt.util.*;




public class TheClient {


	private final static byte CLA_TEST                    = (byte)0x90;
	private final static byte INS_GENERATE_RSA_KEY        = (byte)0xF6;
	private final static byte INS_RSA_ENCRYPT             = (byte)0xA0;
	private final static byte INS_RSA_DECRYPT             = (byte)0xA2;
	private final static byte INS_GET_PUBLIC_RSA_KEY      = (byte)0xFE;
	private final static byte INS_PUT_PUBLIC_RSA_KEY      = (byte)0xF4;
	private PassThruCardService servClient = null;

	boolean DISPLAY = true;


	public TheClient() {
		try {
			SmartCard.start();
			System.out.print( "Smartcard inserted?... " ); 

			CardRequest cr = new CardRequest (CardRequest.ANYCARD,null,null); 

			SmartCard sm = SmartCard.waitForCard (cr);

			if (sm != null) {
				System.out.println ("got a SmartCard object!\n");
			} else
				System.out.println( "did not get a SmartCard object!\n" );

			this.initNewCard( sm ); 

			SmartCard.shutdown();

		} catch( Exception e ) {
			System.out.println( "TheClient error: " + e.getMessage() );
		}
		java.lang.System.exit(0) ;
	}

	private ResponseAPDU sendAPDU(CommandAPDU cmd) {
		return sendAPDU(cmd, true);
	}

	private ResponseAPDU sendAPDU( CommandAPDU cmd, boolean display ) {
		ResponseAPDU result = null;
		try {
			result = this.servClient.sendCommandAPDU( cmd );
			if(display)
				displayAPDU(cmd, result);
		} catch( Exception e ) {
			System.out.println( "Exception caught in sendAPDU: " + e.getMessage() );
			java.lang.System.exit( -1 );
		}
		return result;
	}


	/************************************************
	 * *********** BEGINNING OF TOOLS ***************
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
	 * *********** END OF TOOLS ***************
	 * ****************************************/


	private boolean selectApplet() {
		boolean cardOk = false;
		try {
			CommandAPDU cmd = new CommandAPDU( new byte[] {
				(byte)0x00, (byte)0xA4, (byte)0x04, (byte)0x00, (byte)0x0A,
				    (byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x62, 
				    (byte)0x03, (byte)0x01, (byte)0x0C, (byte)0x06, (byte)0x01
			} );
			ResponseAPDU resp = this.sendAPDU( cmd );
			if( this.apdu2string( resp ).equals( "90 00" ) )
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
			this.servClient = (PassThruCardService)card.getCardService( PassThruCardService.class, true );
		} catch( Exception e ) {
			System.out.println( e.getMessage() );
		}

		System.out.println("Applet selecting...");
		if( !this.selectApplet() ) {
			System.out.println( "Wrong card, no applet to select!\n" );
			System.exit( 1 );
			return;
		} else 
			System.out.println( "Applet selected\n" );

		foo();
	}


	public void runAppliCommands() {
		CommandAPDU cmd;
		ResponseAPDU resp;

		System.out.println( "RSA no padding Automatic Encrypt Check (public key)..." );
		cmd = new CommandAPDU(new byte[] { CLA_TEST,  INS_RSA_ENCRYPT, (byte)0xFF, (byte)0x00, (byte)0x00 });
		resp = sendAPDU(cmd);

		System.out.println( "\nRSA no padding Automatic Decrypt Check (private key)..." );
		cmd = new CommandAPDU(new byte[] { CLA_TEST,  INS_RSA_DECRYPT, (byte)0xFF, (byte)0x00, (byte)0x00 });
		resp = sendAPDU(cmd);

		System.out.println( "\nRSA Key Pair Generation (wait)..." );
		cmd = new CommandAPDU(new byte[] { CLA_TEST,  INS_GENERATE_RSA_KEY, (byte)0x00, (byte)0x00, (byte)0x00 });
		resp = sendAPDU(cmd);
		System.out.println("done");

		System.out.println( "\nRSA no padding Automatic Encrypt Check (public key)..." );
		cmd = new CommandAPDU(new byte[] { CLA_TEST,  INS_RSA_ENCRYPT, (byte)0xFF, (byte)0x00, (byte)0x00 });
		resp = sendAPDU(cmd);

		System.out.println( "\nRSA no padding Automatic Decrypt Check (private key)..." );
		cmd = new CommandAPDU(new byte[] { CLA_TEST,  INS_RSA_DECRYPT, (byte)0xFF, (byte)0x00, (byte)0x00 });
		resp = sendAPDU(cmd);
	}

	private void foo() {
	    sun.misc.BASE64Encoder encoder = new sun.misc.BASE64Encoder();
	    byte[] response;
	    byte[] unciphered; 
	    long seed=0;
	    java.util.Random r = new java.util.Random( seed );

	    byte[] challengeRSA = new byte[128]; 		// size%8==0, coz RSA key 1024bits

	    r.nextBytes( challengeRSA );

	   
	    System.out.println("\nchallenge:\n" + encoder.encode(challengeRSA) + "\n");
	    response = cipherGeneric(INS_RSA_ENCRYPT, challengeRSA);
	    System.out.println("\nciphered is:\n" + encoder.encode(response) + "\n");
	    unciphered = cipherGeneric(INS_RSA_DECRYPT, response);
	    System.out.print("\nunciphered is:\n" + encoder.encode(unciphered) + "\n");
	}


	private byte[] cipherGeneric( byte typeINS, byte[] challenge ) {
		byte[] result = new byte[challenge.length];

		/* Forgage de la requete pour cippher/uncipher*/

		byte[] header = {CLA_TEST,typeINS, 0x00,0x00};

		byte[] optional = new byte[(2+challenge.length)];
		optional[0] = (byte)challenge.length;
		System.arraycopy(challenge, 0, optional, (byte)1,(short)((short)optional[0]&(short)255));
		System.out.print("ici ca plante pas");
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


	public static void main( String[] args ) throws InterruptedException {
		new TheClient();
	}


}
