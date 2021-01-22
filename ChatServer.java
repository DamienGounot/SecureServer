import java.io.*;
import java.net.*;

public class ChatServer{
	public static void main(String[] args) throws Exception{

		ServerSocket serverSocket;
		Socket socket;
		int port = 1234;
			serverSocket = new ServerSocket(port);
			
			while(true){
				socket = serverSocket.accept();
				new ServiceChat(socket);
			}
	}
}