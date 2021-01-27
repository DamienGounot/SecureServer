import java.io.*;
import java.net.*;
import java.util.*;

public class ServiceChat extends Thread {

	boolean debug = false;
    boolean loop = true;

	Socket socket;
	BufferedReader input_clavier;
	PrintStream output_to_server;

	BufferedReader input_server;
	PrintStream output_to_screen;

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

		}
	}

	private synchronized boolean initStreams() { // TODO
		try {
			input = new BufferedReader(new InputStreamReader(System.in());
			output = new PrintStream(socket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
			return false;
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
}