import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Calendar;


public class UDPReceiver {

	public static void main(String[] args) throws IOException {
		int receiverPort = 4119;
		int bufferSize = 1024;
		
		/* Create a DatagramSocket
		 * DatagramSocket is used to receive UDP packets
		 * Do forget to bind the receiving port here
		 */
		DatagramSocket receiverSocket = new DatagramSocket(receiverPort);
		System.out.println("Receiving at port " + String.valueOf(receiverPort) + " ...");
		
		/* Begin to receive UDP packet
		 * No connection is set up for UDP
		 */
		byte[] buffer = new byte[bufferSize];
		while (true) {
			DatagramPacket receiverPacket = new DatagramPacket(buffer, buffer.length);
			receiverSocket.receive(receiverPacket);
			String senderIP = receiverPacket.getAddress().getHostAddress();
			int senderPort = receiverPacket.getPort();
			System.out.println("[" + Calendar.getInstance().getTimeInMillis() + 
				"] Receive from sender (IP: " + senderIP + ", Port: " + 
				String.valueOf(senderPort) + "): " +  new String(buffer, 0, receiverPacket.getLength()));
		}
	}

}
