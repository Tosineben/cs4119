Alden Quimby
adq2101
CS4119 PA1

TO RUN MY CODE:
	0. From a command line, compile by typing "make"
	1. To run my server, please type "java Server"
	2. To run a client, please type "java Client <client_port> <server_ip> <server_port>"

NOTES
	Everything should work as expected.
	
	I handle many extra cases that were not covered in the documentation for completeness.
	I also handle invalid input from the user, and check to make sure all client/server 
	messages are formatted correctly.

	Server.java is the main file for the server, and all of the work is done by ServerHelper
	and GameBoard. The server runs one thread and manages all games and client state.
	
	Client.java is the main file for the clienbt, and all of the work is done by ClientHelper,
	UserListener (on one thread), and ServerListener (on a second thread).

	For my reliable UDP protocol, please see ReliableUDP.java. This wraps UnreliableUDP.java,
	which simply sends and receives with UDP.
