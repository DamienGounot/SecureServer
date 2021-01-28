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
			}
	}

	private void read() { // lit ce qui est saisi par le user
		while (loop) {
		String message = getMessage(input_client);
		send(message);
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
			output_client.println("[ERROR] I/O Exception");
		}
		return msg;
	}

	private void send(String message){
		output_server.println(message);
	}

	private void receive(String message){
		output_client.println(message);
	}
}