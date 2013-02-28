import java.io.IOException;
import java.net.DatagramSocket;

public class ServerListener implements Runnable {

    private UnreliableUDP unreliableUDP; // no packets dropped from server to client
    private DatagramSocket receiverSocket;
    private ClientHelper helper;

    public ServerListener(ClientHelper helper, int port) throws IOException {
        this.unreliableUDP = new UnreliableUDP();
        this.receiverSocket = new DatagramSocket(port);
        this.helper = helper;
    }

    @Override
    public void run() {

        System.out.println("ServerListener starting, port " + receiverSocket.getPort());

        byte[] buffer = new byte[1024];

        while (true) {
            try {

                ReceivedMessage received = unreliableUDP.Receive(receiverSocket, buffer);
                String[] msgParts = received.Message.split(",");
                String command = msgParts[0];

                if (command.equals("acklogin")) {
                    String state = msgParts[1];
                    if (state.equals("F")) {
                        System.out.println("login fail " + helper.Name);
                        helper.IsLoggedIn = false;
                    }
                    else if (state.equals("S")) {
                        System.out.println("login success " + helper.Name);
                        helper.IsLoggedIn = true;
                    }
                    else{
                        //TODO: wtf?
                    }
                }
                else if (command.equals("ackls")) {
                    for (int i = 1; i < msgParts.length; i++) {
                        if (i%2 == 1) {
                            System.out.print(msgParts[i] + " ");
                        }
                        else {
                            System.out.println(msgParts[i]);
                        }
                    }
                    System.out.println("EOF");
                }
                else if (command.equals("request")) {
                    String name = msgParts[1];
                    System.out.println("request from " + name);
                }
                else if (command.equals("ackchoose")) {
                    String name = msgParts[1];
                    String status = msgParts[2];
                    if (status.equals("A")){
                        System.out.println("request accepted by " + name);
                    }
                    else if (status.equals("D")) {
                        System.out.println("request denied by " + name);
                    }
                    else if (status.equals("F")) {
                        System.out.println("request to " + name + " failed");
                    }
                    else {
                        // TODO:wtf?
                    }
                }
                else if (command.equals("play")) {
                    String gameState = msgParts[1];

                    if (gameState.length() != 9) {
                        //TODO: wtf
                    }

                    for (int i = 0; i < 9; i++) {
                        char space = gameState.charAt(i);
                        System.out.print(space == '0' ? '_' : space);
                        System.out.print(' ');
                        if ((i+1)%3 == 0) {
                            System.out.println();
                        }
                    }
                }
                else if (command.equals("ackplay")) {
                    String status = msgParts[1];
                    if (status.equals("O")) {
                        System.out.println("Occupied");
                    }
                    else if (status.equals("T")) {
                        System.out.println("Out of turn");
                    }
                    else{
                        //TODO:wtf
                    }
                }
                else if (command.equals("result")) {
                    String status = msgParts[1];

                    if (status.equals("W")) {
                        System.out.println(helper.Name + " win");
                    }
                    else if (status.equals("L")) {
                        System.out.println(helper.Name + " lose");
                    }
                    else if (status.equals("D")) {
                        System.out.println(helper.Name + " draw");
                    }
                    else{
                        //TODO: wtf
                    }
                }
                else {
                    System.out.println("Unrecognized message from server.");
                }
            }
            catch (IOException e) {
                e.printStackTrace(); //TODO: remove
            }
        }
    }

}
