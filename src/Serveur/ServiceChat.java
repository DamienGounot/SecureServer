import java.io.*;
import java.net.*;
import java.util.*;

public class ServiceChat extends Thread {

	boolean debug = true;

	final static int NB_USERS_MAX = 3;
	static int nb_users = 0;
	static PrintStream[] outputs = new PrintStream[NB_USERS_MAX];
	static ArrayList<String> usernames = new ArrayList<String>();
	static ArrayList<String> passwords = new ArrayList<String>();
	static ServiceChat[] serviceChat = new ServiceChat[NB_USERS_MAX];
	
	Socket socket;
	BufferedReader input;
	PrintStream output;
	String username;
	String password;
	boolean isOnline = false;
	int id_user;
	boolean loop = true;

	String loginRequest = "";

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
			output.println("[SYSTEM] use /login <username> <password> to connect: ");
			loginRequest = getMessage();
			System.out.println("login request: "+loginRequest+">");
			this.username = loginRequest.split(" ")[1]; // pour garder uniquement le username
			System.out.println("Username: <"+this.username+">");
			this.password = loginRequest.split(" ")[2]; // pour garder uniquement le password
			System.out.println("password: <"+this.password+">");

			if (usernameExist(this.username)) {
				output.println("[SYSTEM] Username '"+ this.username + "' was found !");	
				if (checkPassword(this.username, this.password)) {
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
				usernames.add(this.username);
				passwords.add(this.password);
				output.println("[SYSTEM] Successfull login (user added) !");
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

	private boolean checkPassword(String user, String pass) {
		for (int i = 0; i < usernames.size(); i++) {
			String username = usernames.get(i);
			if (username.equals(user)) {
				return passwords.get(i).equals(pass);
			}
		}
		return false;
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
}