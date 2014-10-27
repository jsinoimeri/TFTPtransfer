import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Scanner;


public class ErrorSimulator {

	// test case integer variables: used to recognize test selection and targeted packets
	private int testChosen, packetChosen, blockChosen, corruptionPlaceChosen, delaySeconds;
	
	// IP address of the server machine
	private String addressChosen;
		
	// the server port number to which the requests will be sent
	private static final int RECEIVE_PORT = 68;
	
	// Datagram sockets and packets used for receiving requests
	private DatagramSocket receiveSocket;
	private DatagramPacket receivePacket;
	
	// scanner used to take in test case variables
	private static Scanner choice;
	
	// constructor
	public ErrorSimulator(){
		
		try {
	         receiveSocket = new DatagramSocket(RECEIVE_PORT);
	    } catch (SocketException se) {
	    	 se.printStackTrace();
	         System.exit(1);
	    }
		choice = new Scanner(System.in);
	}
	
	public void run(){
		
		/*
		 * for loop process:
		 * 1. wait for request
		 * 2. ask user the type of error to create 
		 * 3. create thread to deal with it
		 * 4. repeat
		 */
		for(;;) {
			
			byte[] data = new byte[100];
			receivePacket = new DatagramPacket(data, data.length);
			try{
				System.out.println("Waiting to receive request...");
				receiveSocket.receive(receivePacket);
				System.out.println("Request received.");
			} catch (IOException e){
				System.out.println("Error receiving request.");
				System.exit(1);
			}

			System.out.println("Please enter the IP address of the server machine: ");
			
			addressChosen = choice.next();
			
			// default values for test case variables
			testChosen = -1;
			packetChosen = -1;
			blockChosen = -1;
			corruptionPlaceChosen = -1;
			
			// Output message: ask user for test mode values
			System.out.println("Please choose the type of error to be created:");
			System.out.print("Enter \n 0: for normal operation\n"+" 1: to lose a packet\n"+" 2: to delay a packet\n"
															+" 3: to duplicate a packet\n" + " 4: to perform an illegal TFTP operation\n"
																+ " 5: to send packet to client from unknown TID\n");
			testChosen = choice.nextInt();
			
			// ask for further test case values such as targeted packet type and targeted block number
			if(testChosen != 0 && testChosen != 5) {
				
				System.out.println("Enter 1: for RRQ\n"+"2: for WRQ\n" + "3: for DATA packets\n"+"4: for ACK packets\n");
				packetChosen = choice.nextInt();
				
				if(packetChosen == 2 && testChosen == 4) 
					packetChosen = 1;
				
				if(packetChosen == 3 || packetChosen == 4) {
					
					System.out.println("Enter the block number: ");
					blockChosen = choice.nextInt();
				}
			}
			
			if (testChosen == 2){
				System.out.println("Enter the desired number of seconds for delay: ");
				// choice = new Scanner(System.in);
				delaySeconds = choice.nextInt();
			}
			
			if(testChosen == 4){
				System.out.println("Choose the part of the package you want to corrupt: ");
				if(packetChosen == 1||packetChosen == 2){
					System.out.println("1: for opcode\n "+"3: for mode");
				}else if(packetChosen == 3){
					System.out.println("1: for opcode\n"+"2: for block number\n");
				}else if(packetChosen == 4){
					System.out.println("1: for opcode\n "+"2: for block number");
				}
				
				corruptionPlaceChosen = choice.nextInt();
			}
			
			// display chosen test case values
			System.out.println("You chose: \ntest mode = " + testChosen);
			
			if(packetChosen != -1)
				System.out.println("packet with opcode = " + packetChosen);
			
			if(blockChosen != -1)
				System.out.println("with block# = " + blockChosen);
			
			boolean test5 = false;
			
			// create a thread to deal with the request according to the test case values
			if(testChosen == 5) {
				
				testChosen = 0;
				test5 = true;
				DatagramPacket receivePacket2 = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), receivePacket.getAddress(), receivePacket.getPort());
				new ErrorSimulatorThread(receivePacket2, delaySeconds, testChosen, packetChosen, 
						                                     blockChosen, corruptionPlaceChosen, addressChosen).start();
			}
			
			if(testChosen == 1 && (packetChosen == 1 || packetChosen == 2)) {
				
				System.out.println("Simulating a lost request. Error simulator thread not created.");
				
			} else {
				
				if(test5)
					System.out.println("Creating two parallel simulator threads to deal with duplicated requests.");
				else	
					System.out.println("Creating error simulator thread to deal with the request.");
				
				new ErrorSimulatorThread(receivePacket, delaySeconds, testChosen, packetChosen, 
						                                          blockChosen, corruptionPlaceChosen, addressChosen).start();
			}
		}
	}
	
	public static void main(String[] args){
		
		ErrorSimulator es = new ErrorSimulator();
		System.out.println("Error Simulator is starting up...");
		for(;;) {
			es.run();
		}
	}
	
}