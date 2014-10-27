import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class Writer extends Thread {

	DatagramSocket sendReceiveSocket; 		// socket to send and receive data
	String fileDir; 						// the file directory to be written to
	int customerPort; 						// the port to which the acknowledgment packets are supposed to be sent
	boolean clientMode; 					// this is true if the writer thread is operating on a client object
	BufferedInputStream in;
	BufferedOutputStream out;
	InetAddress address; 					// the local IP address on the machine
	byte[] initialData = new byte[516];

	
	/*
	 * 
	 */
	public Writer(String fileDirectory, int port, InetAddress address) throws FileNotFoundException{
		
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException e) {
			System.out.println("Could not find available port.");
			System.exit(0);
		}
		
		fileDir = fileDirectory; 			// the file directory to which the data needs to be written
		customerPort = port;				// the port to send the data to
		clientMode = false; 
		this.address = address;
		
		File f = new File(fileDir);
		try {
			
			// if the file doesn't already exist
			if (!f.exists())
				out = new BufferedOutputStream(new FileOutputStream((String)fileDir));
			else {
				throw new FileNotFoundException();
			}
			
		} catch (FileNotFoundException e) {
						
			Assistant.sendErrorPacket((byte) 6, sendReceiveSocket, customerPort, address);
			
			throw new FileNotFoundException();
		}
	}
	
	public Writer(String fileDirectory, int port, InetAddress address, byte[] data, DatagramSocket ds) throws FileNotFoundException {
	
		this(fileDirectory, port, address);
		clientMode = true;
		initialData = data;
		sendReceiveSocket = ds;
	}
	
	public void run() {
		
		sendAndReceive();
	}
	
	public void sendAndReceive() {
		
		byte MSBlock = 0, LSBlock;
		boolean moreThanOneDataPacket = true;
		
		if(clientMode) {
			
			// reads the incoming data and writes to the file
			try {
				int n = Assistant.lengthFilled(initialData) - 4;
				System.out.println("Received data was filled up to " + n + " bytes.");
				out.write(initialData, 4, n);
				
				// doesn't read any further if the incoming data is less than 512 bytes
				if(n < 512) {
					System.out.println("There was only one data packet to be received.");
					moreThanOneDataPacket = false;
				}
			} catch (IOException e1) {
				System.out.println("Failed to read initially received data or write to file.");
				System.exit(0);
			}		
			
			LSBlock = 1;
			
		} else {
			
			// in this case the writer is not operating under a client object
			// and hence it should send back an acknowledgment packet to the 
			// client that it has received the request
			LSBlock = 0;
		}
		
		// creates an acknowledgment packet and sends it back
		DatagramPacket ackPacket = createAckPacket(MSBlock, LSBlock);
		try {
			// sending ACK packet
			System.out.println("Sending ACK packet with block # = " + Assistant.printBlock(MSBlock, LSBlock));
			sendReceiveSocket.send(ackPacket);
		} catch (IOException e) {
			System.out.println("Could not send acknowledgement packet with block# = " + Assistant.printBlock(MSBlock, LSBlock));
			System.exit(0);
		}
		System.out.println("Sent ACK packet with block # = " + Assistant.printBlock(ackPacket.getData()[2], ackPacket.getData()[3]));
		
		// if more than one data packet is to be received
		if(moreThanOneDataPacket) {
			boolean repeat = true,		// to repeat the while loop
					enter = true; 		// to enter the while loop
			int err = 0;
					
			
			while(enter || repeat) {
				
				if(enter)
					enter = false;
				
				byte[] data = new byte[516];
				DatagramPacket dataPacket = new DatagramPacket(data, data.length);
				
				try {
					
					if(LSBlock == 127) {
						MSBlock++;
						LSBlock = 0;
					} else 
						LSBlock++;
					
					// receive ACK packet
					System.out.println("Waiting to receive data packet with block# = " + Assistant.printBlock(MSBlock, LSBlock));
					sendReceiveSocket.receive(dataPacket);
					
					// checks if the DATA packet received contains expected data
					// if the method returns 0, it will continue normally
					err = Assistant.asExpected(dataPacket, Assistant.DATA_OPCODE, MSBlock, LSBlock);
					
					// if the packet came from an unexpected/unrecognized source
					if(dataPacket.getPort() != customerPort || !(dataPacket.getAddress().equals(address))) {
						System.out.println("******************************\n" + "Packet received from unexpected source."
								+ "\nSending error packet to that source.\n" + "******************************");
			    		Assistant.sendErrorPacket((byte) 5, sendReceiveSocket, dataPacket);
			    		sendReceiveSocket.receive(dataPacket);
			    	}
					
					// since err is not 0, the received packet must contain an error
					else if(err != 0) {
						
						Assistant.sendErrorPacket((byte)4, sendReceiveSocket, dataPacket);
			    		throw new IOException();
			    	}
					
					// if the received packet is an error packet
					if(Assistant.isErrorPacket(dataPacket)) {
						throw new IOException();
					}
						
					System.out.println("Received data packet with block# = " + Assistant.printBlock(dataPacket.getData()[2], dataPacket.getData()[3]));
					int n = Assistant.lengthFilled(data) - 4;
					System.out.println("Received data was filled up " + n + " bytes.");
					
					if(n > 0) {
						if(dataPacket.getData()[3] == LSBlock && dataPacket.getData()[2] == MSBlock)
							out.write(data, 4, n);
						else {
							if(LSBlock == 0) {
								LSBlock = 127;
								if(MSBlock != 0)
									MSBlock--;
							} else 
								LSBlock--;
						}
					}
						
				} catch (IOException e) {
					
					// if disk is full
					if(e.getMessage() != null) {
						if (e.getMessage().compareTo("No space left on device") == 0 
								||e.getMessage().compareTo("There is not enough space on the disk") == 0 
								|| e.getMessage().compareTo("Not enough space")== 0)
						{
							System.out.println("Disk is full");
							Assistant.sendErrorPacket((byte)3, sendReceiveSocket, customerPort, address);
						}
					}
					
					// dealing with other errors 
					else if (dataPacket.getData()[1] == (byte) 5 && dataPacket.getData()[3] == (byte) 5) {
						
						System.out.println("Error 5: Duplicate thread. Terminating self.");
						
					} else if (dataPacket.getData()[1] == (byte) 5 && dataPacket.getData()[3] == (byte) 4) {
						
						System.out.println("Error 4: Data sent was corrupted. Terminating.");
						
					} else if (err != 0) {
						System.out.println("The data received was corrupted.");
					}
					
					else
					{
						System.out.println("Failed to receive data packet with block# = " + Assistant.printBlock(MSBlock, LSBlock));
						System.out.println("Or could not write to file.");
					}
					
					
					repeat = false;
				}
				
				// sending the final ACK packet  
				if(err == 0) {
					customerPort = dataPacket.getPort();
					ackPacket = createAckPacket(MSBlock, LSBlock);
					try {
						System.out.println("Sending ACK packet with block # = " + Assistant.printBlock(MSBlock, LSBlock));
						sendReceiveSocket.send(ackPacket);
					} catch (IOException e) {
						System.out.println("Could not send acknowledgement packet with block# = " + Assistant.printBlock(ackPacket.getData()[2], ackPacket.getData()[3]));
						System.exit(0);
					}
					
					System.out.println("Sent ACK packet with block# = " + Assistant.printBlock(ackPacket.getData()[2], ackPacket.getData()[3]));
					
					if(Assistant.lengthFilled(data) < 512)
						repeat = false;
					
				
				}
			} // end while
		} // end if
		
		try {
			out.close();
		} catch (IOException e) {
			System.out.println("Unable to close the buffered output stream.");
		}
		sendReceiveSocket.close();
		System.out.println("Writer thread terminated.");
	}
	
	/**
	 * method used to create an ACK packet with the specified block number
	 * @param b1 - MSB (most significant byte)
	 * @param b0 - LSB (least significant byte)
	 * @return - datagram packet with opcode = 4 (ACK) and specified block#
	 */
	private DatagramPacket createAckPacket(byte b1, byte b0) {
		
		byte[] ackData = {0, 4, b1, b0};
		DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, address, customerPort);
		return ackPacket;
	}
	
}
