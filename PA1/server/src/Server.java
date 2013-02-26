import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Server {

    public static void main(String[] args) {

        System.out.println("Enter your name: ");

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        String userName = null;

        try {
            userName = br.readLine();
        } catch (IOException ioe) {
            System.out.println("IO error trying to read your name!");
            System.exit(1);
        }

        System.out.println("Thanks for the name, " + userName);

    }
}
