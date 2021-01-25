import java.io.*;
import java.net.*;


public class ServiceChat extends Thread {


	final static int NB_USERS_MAX = 3;
	BufferedReader input;
	PrintStream output;
	static PrintStream[] outputs = new PrintStream[NB_USERS_MAX];
	Socket socket;
	static int nb_users = 0;
	//static String[] usernames = new String[NB_USERS_MAX];
	static ServiceChat[] serviceChat = new ServiceChat[NB_USERS_MAX];
	String username;
	int id_user;
	boolean loop= true;


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
			output.println("Server is full, please try again later...");
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}else{

			//output.println("DEBUG: ID output = "+nb_users);
			this.id_user = nb_users;
			serviceChat[id_user] = this;
			outputs[nb_users] = output;
			nb_users++;
	
			output.println("Welcome on chat");
			output.println("Enter your username: ");
			this.username = getMessage();
			
			// do{
			// 	output.println("Enter your username: ");
			// 	this.username = getMessage();
			// }while(!isUsernameAvailable(this.username));
	
			//output.println("DEBUG: ID user = "+id_user);

			//usernames[id_user] = this.username;
			
			broadCast("[SYSTEM] " + username + " has join the chat");
			//output.println("DEBUG: nbUsers = "+nb_users);
			return true;
		}
	}

	public synchronized void broadCast(String input) {
		 //output.println("DEBUG Broadcast: nbUsers = "+nb_users);
		for (int i = 0; i < nb_users; i++) {
			try {
				 //output.println("DEBUG Broadcast: send to rang = "+i);
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
		switch (msg) {
			case "/quit":
				try {
					outputs[id_user] = outputs[nb_users-1];
					output.println("[DEBUG] outputs["+id_user+"]= outputs["+(nb_users-1)+"]");
					output.println("[DEBUG] BEFORE UPDATE, UserID: "+serviceChat[(nb_users-1)].id_user);
					serviceChat[(nb_users-1)].id_user = this.id_user;
					output.println("[DEBUG] AFTER UPDATE, UserID: "+serviceChat[(nb_users-1)].id_user);
					nb_users --;
					output.println("[DEBUG] nbUser ="+nb_users);
					broadCast( "[SYSTEM] " + username + " has left!" );
					socket.close();
					loop = false;
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("Erreur /quit");
				}
				break;
				
			default:
				broadCast("<" + username + "> " + msg);
		}
	}

	// 	private boolean isUsernameAvailable(String name){
	// 		for(int i = 0; i< nb_users; i++){
	// 			if(usernames[i].equals(name)){
	// 				output.println("Error: This username is already taken !");
	// 				return false;
	// 			}
	// 		}
	// 		return true;
	// 	}
}