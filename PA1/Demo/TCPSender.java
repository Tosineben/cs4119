import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;


public class TCPSender {

	public static void main(String[] args) throws UnknownHostException, IOException {
		String receiverIP = "127.0.0.1";
		int receiverPort = 4119;
		
		/* Create a Socket
		 * Connect to the receiver
		 */
		Socket senderSocket = new Socket(receiverIP, receiverPort);
		System.out.println("Connect to the receiver whose IP is " + receiverIP + ", Port is " 
				+ String.valueOf(receiverPort) + " ...");
		
		// Sender data
		OutputStream outStream = senderSocket.getOutputStream();
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			String inputString = input.readLine();
			outStream.write(inputString.getBytes());
			System.out.println("Send to receiver: " + inputString);
		}
	}

}
