import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;


public class UDPSender {

	public static void main(String[] args) throws IOException {
		String receiverIP = "127.0.0.1";
		int receiverPort = 4119;
		int bufferSize = 1024;
		
		// Create DatagramSocket
		DatagramSocket senderSocket = new DatagramSocket();
		
		// Begin to send
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		byte[] buffer = new byte[bufferSize];
		while (true) {
			String inputString = input.readLine();
			buffer = inputString.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(buffer, buffer.length, 
					InetAddress.getByName(receiverIP), receiverPort);
			senderSocket.send(sendPacket);
			System.out.println("Send to receiver: " + inputString);
		}
	}

}
