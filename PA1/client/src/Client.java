import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Client {

    public static void main(String[] args) throws IOException {

        //TODO: remove this debug hack
        if (args.length == 1) {
            String firstArg = args[0];
            args = new String[3];
            args[0] = firstArg;
            args[1] = "127.0.0.1";
            args[2] = "4119";
        }

        int clientPort;
        String serverIP;
        int serverPort;

        try {
            clientPort = Integer.parseInt(args[0]);
            serverIP = args[1];
            serverPort = Integer.parseInt(args[2]);
        }
        catch (Exception e) {
            System.out.println("Invalid arguments: java Client <client_port> <server_ip> <server_port>");
            System.exit(1);
        }

        System.out.println("Welcome to TIC TAC TOE, here are available commands:");
        System.out.println("login <name>");
        System.out.println("ls");
        System.out.println("choose <name2>");
        System.out.println("accept <name1>");
        System.out.println("deny <name1>");
        System.out.println("play <number>");
        System.out.println("logout");

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        ClientHelper helper = new ClientHelper();

        // accept user input forever
        while (true) {

            System.out.println("\nPlease enter a command:");

            String cmd = br.readLine();

            if (cmd.equals("ls")) {
                helper.QueryList();
            }
            else if (cmd.equals("logout")) {
                helper.Logout();
            }
            else {

                String[] cmdParts = cmd.split(" ");

                // all remaining commands should have 2 words
                if (cmdParts.length != 2) {
                    System.out.println("Oops, I don't recognize that command, try again.");
                    continue;
                }

                String input = cmdParts[1].trim();

                if (cmd.startsWith("login")) {
                    helper.Login(input);
                }
                else if (cmd.startsWith("choose")) {
                    helper.ChoosePlayer(input);
                }
                else if (cmd.startsWith("accept")) {
                    helper.AcceptRequest(input);
                }
                else if (cmd.startsWith("deny")) {
                    helper.DenyRequest(input);
                }
                else if (cmd.startsWith("play")) {
                    int number;

                    try{
                        number = Integer.parseInt(input);
                    }
                    catch (NumberFormatException e) {
                        System.out.println("Oops, I don't recognize that command, try again.");
                        continue;
                    }

                    helper.PlayGame(number);
                }
                else {
                    System.out.println("Oops, I don't recognize that command, try again.");
                }
            }

        } //while

    } //main
}
