import java.io.*;
import java.net.*;

public class ClientChat extends Thread {

	boolean debug = false;
	boolean loop = true;

	Socket socket;

	BufferedReader input_client;
	PrintStream output_client;

	BufferedReader input_server;
	PrintStream output_server;

	public static void main(String[] args) throws Exception{

		Socket socket = new Socket("localhost",1234);
		new ClientChat(socket);
	}

	public ClientChat(Socket socket) {
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

	private void listen() { // ecoute ce qui arrive du serveur
		while (loop) {
			String message = getMessage(input_server);
			receive(message);
			if(message.startsWith("Server is full")){
				System.exit(0);
			}
			}
	}

	private void read() { // lit ce qui est saisi par le user
		while (loop) {
		String message = getMessage(input_client);
		send(message);
		if(message.equals("/quit")){
			output_client.println("Exiting system...");
			System.exit(0);
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
		try {
			output_client.println(message);
		} catch (Exception e) {
			output_client.println("[ERROR] receive()");
		}
	}
}