import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.String;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPSender {

	public static void main(String[] args) throws IOException {
		String receiverIP = "127.0.0.1";
		int receiverPort = 5000;
        int packetId = 0;

		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        ReliableUDP reliableUDP = new ReliableUDP();

        while (true) {
            String message = input.readLine() + "," + ++packetId;
            reliableUDP.Send(receiverIP, receiverPort, message);
			System.out.println("Send to receiver: " + message);
		}
	}
}
