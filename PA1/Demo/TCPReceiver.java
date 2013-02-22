import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;


public class TCPReceiver {

	public static void main(String[] args) throws IOException {
		int receiverPort = 4119;
		int bufferSize = 1024;
		
		/* Create a ServerSocket
		 * ServerSocket is used to listen to TCP connection from other hosts
		 * Do forget to bind the listening port here
		 */
		ServerSocket receiverServerSocket = new ServerSocket(receiverPort);
		System.out.println("Listening at port " + String.valueOf(receiverPort) + " ...");
		
		/* Create a Socket
		 * Accept TCP connection
		 */
		Socket receiverSocket = receiverServerSocket.accept();
		String senderIP = receiverSocket.getInetAddress().getHostAddress();
		int senderPort = receiverSocket.getPort();
		System.out.println("Accept connection from IP: " + senderIP + ", Port: " + String.valueOf(senderPort));
		
		// Read received Data
		byte[] buffer = new byte[bufferSize];
		int msgSize = 0;
		InputStream inStream = receiverSocket.getInputStream();
		while (true) {
			while ((msgSize = inStream.read(buffer)) > 0) {
				System.out.println("Receive from sender: " +  new String(buffer, 0, msgSize));
			}
		}
		
	}

}
