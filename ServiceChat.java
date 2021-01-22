import java.io.*;
import java.net.*;

public class ServiceChat extends Thread {

	final static int NB_USERS_MAX = 3;
	BufferedReader input;
	PrintStream output;
	static PrintStream[] outputs = new PrintStream[NB_USERS_MAX];
	Socket socket;
	static int nb_users = 0;

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
		output.println("Welcome on chat");
        outputs[nb_users] = output;
        nb_users ++;

        return true;
    }

    public synchronized void broadCast(String input){
		for(int i = 0; i < nb_users; i++ )
		{
			try{
				outputs[i].println(input);
			}catch(Exception e){
				continue;
			}
		}
    }
    
    private String getMessage(){
		String msg="";
		try {
			msg = input.readLine();
		} catch (IOException e) {
			output.println("[ERROR] I/O Exception");
		}
		return msg;
	}
}