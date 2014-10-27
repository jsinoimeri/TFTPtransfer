import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;


public class ErrorSimulatorThread extends Thread {
	
	// datagram packets and sockets to, receive or send packets, to or from, client or server
	// socket that sends and receives from server
	
	DatagramSocket serverSocket, 
	// socket that sends and receives from server
	               clientSocket;
	// the packet that is first received that contains the request from the client
	
	DatagramPacket requestPacket,
	// packets used to send and receive from server
					sendPacketS, receivePacketS,
	// packets used to send and receive from client
					sendPacketC, receivePacketC;
	
	// the ports of the server and client sockets
	private int serverPort, clientPort;
	
	// the IP addresses of the server and client machines
	InetAddress serverAdd, clientAdd;
	
	// requestType = 1 for RRQ, = 2 for WRQ
	private int requestType;
	
	// error type, packet type and block # to perform action on
	private int testChosen, packetTypeChosen, blockChosen, corruptionPlaceChosen;
	
	// the timeout time in second in case of delay error, -1 if not
	private int timeInSeconds;
	
	// the numbers of seconds any socket should wait before timing out
	private static final int TIME_OUT_SECONDS = 5;
	
	public ErrorSimulatorThread(DatagramPacket request, int time, int testChosen,
													int packetTypeChosen, int blockChosen, int corruptionPlaceChosen, String addressChosen) {
		
		try {
			serverSocket = new DatagramSocket();
			clientSocket = new DatagramSocket();
		} catch (SocketException e) {
			System.out.println("Could not create socket to deal with either client or server.");
		}
		
		requestPacket = request;
		
		// client port/address information
		clientPort = request.getPort();
		clientAdd = request.getAddress();
		
		// server port/address information
		// initially the request is sent to the server, hence the port should be 69
		// it changes later to the port for the socket used in the thread dealing with the request
		serverPort = 69;
		try {
			serverAdd = InetAddress.getByName(addressChosen);
		} catch (UnknownHostException e) {
			System.out.println("Server machine IP address was invalid.");
		}
		
		// test case variables
		this.testChosen = testChosen;
		this.packetTypeChosen = packetTypeChosen;
		this.blockChosen = blockChosen;
		this.corruptionPlaceChosen = corruptionPlaceChosen;
		timeInSeconds = time;
	}

	public void run() {
		
		System.out.println("Received request with: \ntest mode = " + testChosen);
			
		if(packetTypeChosen != -1)
				System.out.println("packet with opcode = " + packetTypeChosen);
			
		if(blockChosen != -1)
				System.out.println("with block# = " + blockChosen);
		
		requestType = requestPacket.getData()[1];

		if(testChosen == 4) {
			
			// the packet to be corrupted is a RRQ or a WRQ
			if(packetTypeChosen == 1) {
				
				//check if the place to corrupt is the opcode, the filename or the mode
				if(corruptionPlaceChosen == 1){
					
					//mess up the value of the opcode
					requestPacket.getData()[0] = 8;
					requestPacket.getData()[1] = 8;
				
				} else if(corruptionPlaceChosen == 3) {
					
					//replace the mode string with something else
					int leng = 1; // opcode
					for(int i = 2; requestPacket.getData()[i] != 0b0; i++)
						 leng++;
							
					leng++; //the 0 after the file name
					
					//mode follows, so corrupting by replacing a character
					requestPacket.getData()[leng+1] = new String(",").getBytes()[0];
				}
			}
		}
			
		try {
			
			// change the port to send the request to, to the server's receiving socket's port
			// and it's address to the IP address of the server machine
			requestPacket.setPort(serverPort);
			requestPacket.setAddress(serverAdd);
			
			// send the request
			System.out.println("Sending request.");
			serverSocket.send(requestPacket);
			System.out.println("Sent request.");
			
		} catch (IOException e) {
			System.out.println("Could not forward request.");
			System.exit(0);
		}		
		
		// array of bytes to receive data or acknowledgement through datapacket
		byte[] data = new byte[516], 
				ack = new byte[50];
		
		// a character that signifies whether the side being dealt with is the client's or the server's
		char engagedSide = 's';
		
		// the number of times either the client or the server socket has timed out continuously
		int timeOutCount = 0;
		
		try {
			
			// variable that decides whether the action (according to the running test mode) 
			// should be performed or not
			boolean performAction = true;
			
			// setting the time after which the sockets should time out
			clientSocket.setSoTimeout(TIME_OUT_SECONDS * 1000);
			serverSocket.setSoTimeout(TIME_OUT_SECONDS * 1000);
			
			// the main body that alternates between listening to the client and the server ends
			while(timeOutCount < 3) {
				
				if(engagedSide == 's') {
					
					if(timeOutCount == 0)
						// this message lets the user know which side is presently engaged
						System.out.println("Server port engaged." );
					
					
					try {
						
						if(requestType == 1) {
							
							if(timeOutCount == 0)
								System.out.println("Waiting to receive DATA packet from server.");
							
							// receive DATA packet from server 
							receivePacketS = new DatagramPacket(data, data.length);
							serverSocket.receive(receivePacketS);
							System.out.println("Received DATA packet with block# = " + Assistant.printBlock(receivePacketS.getData()[2], receivePacketS.getData()[3]));
						}
						
						if(requestType == 2) {
							
							if(timeOutCount == 0)
								System.out.println("Waiting to receive ACK packet from server.");
							
							// receive ACK packet from server
							receivePacketS = new DatagramPacket(ack, ack.length);
							serverSocket.receive(receivePacketS);
							
							if(!Assistant.isErrorPacket(receivePacketS)) {
								System.out.println("Received ACK packet with block# = " + Assistant.printBlock(receivePacketS.getData()[2], receivePacketS.getData()[3]));
								receivePacketS.setData(Assistant.trimArrayToAck(receivePacketS));
							}
							
						}
						
						// reassign values for server port/address
						serverPort = receivePacketS.getPort();
						serverAdd = receivePacketS.getAddress();
						
						// prepare new packet to send to client
						sendPacketC = receivePacketS;
						sendPacketC.setPort(clientPort);
						sendPacketC.setAddress(clientAdd);
						
						// if the test case variables match
						if(performAction(receivePacketS, packetTypeChosen, blockChosen) && performAction) {
							
							// to lose the packet, do nothing (which is equivalent to not sending the packet and hence losing it)
							if(testChosen == 1);
								
							// to delay packet before sending
							if(testChosen == 2) {
								
								System.out.println("Thread going to sleep for " + timeInSeconds + " seconds.");
								Thread.sleep(timeInSeconds * 1000);
								System.out.println("Sending received packet to client.");
								clientSocket.send(sendPacketC);
								System.out.println("Sent packet with block# = " + Assistant.printBlock(sendPacketC.getData()[2], sendPacketC.getData()[3]));
							}
							
							// to duplicate packet and send
							if(testChosen == 3) {
								System.out.println("Duplicating data packets and sending both.");
								clientSocket.send(sendPacketC);
								clientSocket.send(sendPacketC);
							}
							
							// to corrupt packet before sending
							if(testChosen == 4){
								byte[] newData = sendPacketC.getData();
								// the packet to be corrupted is a DATA	
								System.out.println("Corrupting data packet with block# = " + blockChosen);
								if (packetTypeChosen == 3){
									
									if(corruptionPlaceChosen == 1){
										
										//mess up the value of the opcode
										newData[0] = 8;
										newData[1] = 8;
									}else if(corruptionPlaceChosen == 2){
										
										//mess up the block number
										newData[3] = 7;
										newData[4] = 7;
									}else if(corruptionPlaceChosen == 3){
										
										//replacing data with an empty array
										byte[] replacing = new byte [512];
										System.arraycopy(replacing, 0, newData, 4, replacing.length);
									}
								//the packet to be corrupted is an ACK
								}else if(packetTypeChosen == 4){
									
									if(corruptionPlaceChosen == 1){
										//mess up the value of the opcode
										newData[0] = 8;
										newData[1] = 8;
									}else if(corruptionPlaceChosen == 2){
										//mess up the block number
										newData[2] = 55;
										newData[3] = 55;
									}
								}
								sendPacketC.setData(newData, 0, newData.length);
								clientSocket.send(sendPacketC);
							}
							
							// since the test should run normally once the error has been created
							testChosen = 0;
							performAction = false;
							
						} else {
							
							System.out.println("Sending received packet to client.");
							clientSocket.send(sendPacketC);
							System.out.println("Sent packet with block# = " + Assistant.printBlock(sendPacketC.getData()[2], sendPacketC.getData()[3]));
						}
						timeOutCount = 0;
						
					} catch (SocketTimeoutException e) {
						
						if(timeOutCount == 0)
							System.out.println("Switching between ports to receive any response from server or client.");
						
						// if any socket times out, the count should increment
						timeOutCount++;
					}
					
					// the engaged side will now be the client's
					engagedSide = 'c';
					
				} else if (engagedSide == 'c') {
					
					if(timeOutCount == 0)
						// this message lets the user know which side is presently engaged
						System.out.println("Client port engaged." );
					
					try {
						if(requestType == 1) {
							
							if(timeOutCount == 0)
								System.out.println("Waiting to receive ACK packet from client.");
							
							// receive ACK packet from client
							receivePacketC = new DatagramPacket(ack, ack.length);
							clientSocket.receive(receivePacketC);
							
							if(!Assistant.isErrorPacket(receivePacketS)) {
								System.out.println("Received ACK packet with block# = " + Assistant.printBlock(receivePacketS.getData()[2], receivePacketS.getData()[3]));
								receivePacketS.setData(Assistant.trimArrayToAck(receivePacketS));
								
							}
							
						}
						
						if(requestType == 2) {
							
							if(timeOutCount == 0)
								System.out.println("Waiting to receive DATA packet from client.");
							
							// receive DATA packet from client
							receivePacketC = new DatagramPacket(data, data.length);
							clientSocket.receive(receivePacketC);
							System.out.println("Received DATA packet with block# = " + Assistant.printBlock(receivePacketC.getData()[2], receivePacketC.getData()[3]));
						}
						
						// reassign values for client port/address
						clientPort = receivePacketC.getPort();
						clientAdd = receivePacketC.getAddress();

						// prepare new packet to send to server
						sendPacketS = receivePacketC;
						sendPacketS.setPort(serverPort);
						sendPacketS.setAddress(serverAdd);
						
						// if the test case variables match
						if(performAction(receivePacketC, packetTypeChosen, blockChosen) && performAction) {
							
							// to lose the packet, do nothing (which is equivalent to not sending the packet and hence losing it)
							if(testChosen == 1);
							
							// to delay the packet before sending it
							if(testChosen == 2) {
								System.out.println("Thread going to sleep for " + timeInSeconds + " seconds.");
								Thread.sleep(timeInSeconds * 1000);
								serverSocket.send(sendPacketS);
							}
							
							// to duplicate the packet and send both
							if(testChosen == 3) {
								System.out.println("Duplicating data packets and sending both.");
								serverSocket.send(sendPacketS);
								serverSocket.send(sendPacketS);
							}
							
							// to corrupt the packet data before sending it
							if(testChosen == 4){
								byte[] newData = sendPacketS.getData();
								// the packet to be corrupted is a DATA	
								System.out.println("Corrupting data packet with block# = " + blockChosen);
								if (packetTypeChosen == 3){
									
									if(corruptionPlaceChosen == 1){
										//mess up the value of the opcode
										newData[0] = 8;
										newData[1] = 8;
										
									}else if(corruptionPlaceChosen == 2){
										//mess up the block number
										newData[3] = 7;
										newData[4] = 7;
									}else if(corruptionPlaceChosen == 3){
										//replacing data with an empty array
										byte[] replacing = new byte [512];
										System.arraycopy(replacing, 0, newData, 4, replacing.length);
									}
								//the packet to be corrupted is an ACK
								}else if(packetTypeChosen == 4){
									if(corruptionPlaceChosen == 1){
										//mess up the value of the opcode
										newData[0] = 8;
										newData[1] = 8;
									}else if(corruptionPlaceChosen == 2){
										//mess up the block number
										newData[2] = 55;
										newData[3] = 55;
									}
								}
								sendPacketS.setData(newData, 0, newData.length);
								serverSocket.send(sendPacketS);
							}
							
							// since the test should run normally once the error has been created
							testChosen = 0;
							performAction = false;
							
						} else {
							
							System.out.println("Sending received packet received to server.");
							serverSocket.send(sendPacketS);
							System.out.println("Sent packet with block# = " + Assistant.printBlock(sendPacketS.getData()[2], sendPacketS.getData()[3]));
						}
						
						timeOutCount = 0;
					} catch (SocketTimeoutException e) {

						if(timeOutCount == 0)
							System.out.println("Switching between ports to receive any response from server or client.");
						
						// if any socket times out, the count should increment
						timeOutCount++;
					}
					
					// the engaged side will now be the server's
					engagedSide = 's';
				}
			}
			
		} catch (IOException e) {
			
			System.out.println("Failed to perform simulation due to I/O error.");
			
		} catch (InterruptedException e) {
			
			System.out.println("Failed to perform simulation due to interruption.");
		}
		
		if(timeOutCount > 0)
			System.out.println("No packets received from client or server.");
		
		 System.out.println("ErrorSimulator thread terminated.");
	}
	
	public boolean performAction(DatagramPacket target, int packetType, int blockNo) {
		
		int opcode = (int) target.getData()[1],
			block = (int) Assistant.printBlock(target.getData()[2], target.getData()[3]);
		
		return (packetType == opcode && blockNo == block);
	}
}
