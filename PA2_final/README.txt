Alden Quimby
adq2101
CS4119 PA2

TO RUN MY CODE:
	1. From a command line, compile by typing "make"
	2. Type "java SRNode <args>", "java DVNode <args>", or "java SDNode <args>",
	   where <args> for each is based on the assignment description

NOTES
	Everything should work as expected.
	
	For DVNode and SDNode, please look at the MessageCreator subclasses
	to see how I formatted the payload of each packet.

	For SDNode, I basically copied the DVNode and SRNode code into one
	file, and added the ability to handle "send" and "change" commands.
	Note that the node has a separate Selective Repeat implementation
	for each neighbor. I did this to ensure that packet numbers and
	the sender/receiver windows did not get messed up.

	Under certain circumstances when handling the "send" command, my program will get 
	into a locked state. You may or may not come across this, it seems to happen to me
	roughly 1/50 times I try to send. Please try sending again if things appear to freeze.


PROBLEM WITH THE ASSIGNMENT
	When caluclating DV routing tables, the weight should be: 
	1 / (1 - LossRate)^2 (see thread https://piazza.com/class#spring2013/csee4119/204).
	I chose to follow the assignment and use 1 / (1 - LossRate), which could lead to
	incorrect routing choices by SDNode. Consider the following loss rates:

	1111->2222 0.1
	1111->3333 0.5
	2222->3333 0.3

	Using weight = 1 / (1 - LossRate), 1111 thinks it can get to node 3333 
	in 1.429, and that it would take 1.111+2.000=3.111 to get there by 
	going through node 2222, so it chooses to go directly to node 3333.

	Using weight = 1 / (1 - LossRate)^2, 1111 thinks it can get to node 3333 
	in 4.000, and that it would take 1.235+2.041=3.276 to get there by going
	through node 2222, so it chooses to go through node 2222 to get to 3333.

	Clearly this matters quite a bit, and because the actual time spent scales
	with 1 / (1 - LossRate)^2, but my algorithm uses 1 / (1 - LossRate), 
	the timing output from SDNode will show that the routing table is wrong
	in certain cases.

	I just wanted to point this out so that I do not get penalized for 
	following the assignment description.
