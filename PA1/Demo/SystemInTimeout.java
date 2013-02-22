import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;


public class SystemInTimeout {

	/**
	 * @param args
	 * @throws IOException 
	 */
	private String str = "";

	TimerTask task = new TimerTask() {
		public void run() {
			if (str.equals("")) {
				System.out.println("you input nothing. exit...");
				System.exit(0);
			}
		}
	};

	public void getInput() throws Exception {
		Timer timer = new Timer();
		timer.schedule(task, 3 * 1000);

		System.out.println("Input a string within 3 seconds: ");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		str = in.readLine();

		timer.cancel();
		System.out.println("you have entered: " + str);
	}

	public static void main(String[] args) {
		try {
			(new SystemInTimeout()).getInput();
		} catch (Exception e) {
			System.out.println(e);
		}
		System.out.println("main exit...");
	}

}
