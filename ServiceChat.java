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
			output.println("Server is full (" +nb_users+"/"+NB_USERS_MAX+") , please try again later...");
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

			output.println("Welcome on chat (" +nb_users+"/"+NB_USERS_MAX+")");
			output.println("Enter your username: ");
			this.username = getMessage();

			if (usernameExist(this.username)) {
				output.println("Username '"+ this.username + "' was found !");
				output.println("Enter associated password...");
				this.password = getMessage();
				if (checkPassword(this.username, this.password)) {
					broadCast("[SYSTEM] " + username + " has join the chat (" +nb_users+"/"+NB_USERS_MAX+")");
					this.isOnline = true;
					userList();
					if(debug)System.out.println("[DEBUG] Username: "+this.username+" , ID: "+this.id_user);				
					return true;
				} else {
					disconnect("error");
					return false;
				}
			} else {
				output.println("Enter your password: ");
				this.password = getMessage();
				usernames.add(this.username);
				passwords.add(this.password);
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
		String[] command = msg.split(" ");
		switch (command[0]) {
			case "/quit":
				disconnect("");
				break;
			case "/list":
				userList();
				break;
			case "/send":
				int userID = getUserID(command[1]);
				if(userID == -1){
					output.println("Error: The user '"+command[1]+"' does not exist or is not online !");
				}else{
					send(userID, command);
				}
				
				break;	
			default:
				broadCast("<" + this.username + "> " + msg);
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
			output.println("User List: ");
		for (int i=0; i<nb_users;i++) {
			if(serviceChat[i].isOnline)
				output.println(serviceChat[i].username);
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
		outputs[userID].print("[Private]<" + this.username + "> ");
		for (int i = 2; i < command.length; i++) {
			outputs[userID].print(command[i]+" ");
		}
		outputs[userID].println();
	}
}