import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Calendar;


public class UDPReceiver {

	public static void main(String[] args) throws IOException {
		int receiverPort = 4119;

		DatagramSocket receiverSocket = new DatagramSocket(receiverPort);
		System.out.println("Receiving at port " + receiverPort + " ...");

        ReliableUDP reliableUDP = new ReliableUDP();

        while (true) {
            ReceivedMessage message = reliableUDP.Receive(receiverSocket);
		}
	}

}
