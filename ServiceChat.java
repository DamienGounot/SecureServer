import java.io.*;
import java.net.*;
import java.util.*;

public class ServiceChat extends Thread {

	boolean debug = false;

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

			if(debug)output.println("DEBUG: ID output = "+nb_users);
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
		switch (msg) {
			case "/quit":
				disconnect("");
				break;
			case "/list":
				userList();
				break;
			default:
				broadCast("<" + username + "> " + msg);
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

			if(debug)output.println("[DEBUG] outputs[" + id_user + "]= outputs[" + (nb_users - 1) + "]");
			if(debug)output.println("[DEBUG] BEFORE UPDATE, UserID: " + serviceChat[(nb_users - 1)].id_user);
			
			serviceChat[(nb_users - 1)].id_user = this.id_user;
			
			if(debug)output.println("[DEBUG] AFTER UPDATE, UserID: " + serviceChat[(nb_users - 1)].id_user);
			
			serviceChat[this.id_user] = serviceChat[(nb_users-1)]; //le dernier servicechat devient celui qui se déco
			
			nb_users--;
			
			if(debug)output.println("[DEBUG] nbUser =" + nb_users);
				
			
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
}