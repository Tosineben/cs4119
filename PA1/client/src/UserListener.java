import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class UserListener implements Runnable {

    private ClientHelper helper;

    public UserListener(ClientHelper helper) {
        this.helper = helper;
    }

    @Override
    public void run() {

        System.out.println("Welcome to TIC TAC TOE, here are available commands:");
        System.out.println("login <name>");
        System.out.println("ls");
        System.out.println("choose <name2>");
        System.out.println("accept <name1>");
        System.out.println("deny <name1>");
        System.out.println("play <number>");
        System.out.println("logout");

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        // accept user input forever
        while (true) {

            System.out.println("\nPlease enter a command:\n");

            String userInput;
            try {
                userInput = br.readLine();
            } catch (IOException e) {
                System.out.println("Oops, I don't understand that input.");
                continue;
            }

            if (userInput.equals("ls")) {
                helper.QueryList();
            }
            else if (userInput.equals("logout")) {
                helper.Logout();
            }
            else {

                String[] cmdParts = userInput.split(" ");

                // all remaining commands should have 2 words
                if (cmdParts.length != 2) {
                    System.out.println("Oops, I don't recognize that command, try again.");
                    continue;
                }

                String command = cmdParts[0];
                String input = cmdParts[1].trim();

                if (command.equals("login")) {
                    helper.Login(input);
                }
                else if (command.equals("choose")) {
                    helper.ChoosePlayer(input);
                }
                else if (command.equals("accept")) {
                    helper.AcceptRequest(input);
                }
                else if (command.equals("deny")) {
                    helper.DenyRequest(input);
                }
                else if (command.equals("play")) {
                    Integer number = Utility.TryParseInt(input);
                    if (number == null) {
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
    }
}
