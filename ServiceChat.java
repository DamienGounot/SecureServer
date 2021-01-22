import java.io.*;
import java.net.*;

public class ServiceChat extends Thread {

	final static int NB_USERS_MAX = 2;
	BufferedReader input;
	PrintStream output;
	static PrintStream[] outputs = new PrintStream[NB_USERS_MAX];
	Socket socket;
	static int nb_users = 0;
	static String[] usernames = new String[NB_USERS_MAX];

	String username = "";
	int id_user = 0;

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
		while (true) {
			String message = getMessage();
			broadCast(message);
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
			output.println("Server is full, please try again later...");
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}
		// output.println("DEBUG: rang output = "+nb_users);
		outputs[nb_users] = output;
		nb_users++;
		this.id_user = (nb_users - 1);
		output.println("Welcome on chat");
		output.println("Enter your username: ");
		this.username = getMessage();
		// output.println("DEBUG: rang username = "+id_user);
		usernames[id_user] = this.username;
		output.println("Welcome on chat mister " + this.username + " !");
		// output.println("DEBUG: nbUsers = "+nb_users);
		return true;
	}

	public synchronized void broadCast(String input) {
		// output.println("DEBUG Broadcast: nbUsers = "+nb_users);
		for (int i = 0; i < nb_users; i++) {
			try {
				// output.println("DEBUG Broadcast: send to rang = "+i);
				outputs[i].println(this.username + ">" + input);
			} catch (Exception e) {
				continue;
			}
		}
	}

	// public synchronized void sendToUser(String userToSend){
	// 	for(int i = 0; i < nb_users; i++ )
	// 	{
	// 		if()
	// 		{	
	// 			try{
	// 				outputs[i].println(this.username+">"+input);
	// 			}catch(Exception e){
	// 				continue;
	// 			}
	// 		}
	// 	}
    // }

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
		switch (msg) {
			case "/quit":
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			break;
			default:
		}
	}
}