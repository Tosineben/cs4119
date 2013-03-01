import java.io.*;

public class UserListener implements Runnable {

    private ClientHelper helper;

    public UserListener(ClientHelper helper) {
        this.helper = helper;
    }

    @Override
    public void run() {

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        // accept user input forever
        while (true) {

            String userInput;
            try {
                userInput = br.readLine();
            } catch (IOException e) {
                InvalidUserInput();
                continue;
            }

            if (userInput.equals("ls")) {
                helper.QueryList();
            }
            else if (userInput.equals("logout")) {
                helper.Logout();
            }
            else {

                String[] msgParts = userInput.split(" ");

                // all remaining commands should have 2 words
                if (msgParts.length != 2) {
                    InvalidUserInput();
                    continue;
                }

                String command = msgParts[0];
                String input = msgParts[1].trim();

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
                        InvalidUserInput();
                    }
                    else {
                        helper.PlayGame(number);
                    }
                }
                else {
                    InvalidUserInput();
                }
            }

        } //while
    }

    private void InvalidUserInput() {
        System.out.println("Oops, I don't recognize that command, try again.");
    }
}
