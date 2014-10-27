/**
 * Reader class reads from a file specified by client
 * and sends the data in 512 byte blocks to the
 * appropriate end user.
 * 
 * @author Jeton Sinoimeri
 * 
 * @created May 11, 2014
 * @modified May 11, 2014
 * 
 */

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;




public class Reader extends Thread
{
	
	private static final int TIME_OUT_SECONDS = 4;           // time out for the receive socket
	
	private static InetAddress address;                      // the local address of the receiving computer
	
	private DatagramSocket send_receive_socket;              // Datagram socket for sending and receiving
	
	private byte LSBlock,                                    // least sig block number for verifying acknowledgment packets
	             MSBlock;                                    // most sig block number for verifying acknowledgment packets
	
	private int send_port;                                   // the port number to send data to
	
	private String file_dir;                                 // the filename and directory to read from
		
	
 
	/**
	 * Constructor for the Reader class.
	 * 
	 * @param filedir   String containing the directory along with the file name 
	 *                  in the form (\directory\file_name.txt)
	 *                  
	 * @param requestPacket  DatagramPacket containing the port number and local
	 *                       address of the machine receiving the data sent from
	 *                       Reader
	 * 
	 */
	
	public Reader(String filedir, DatagramPacket requestPacket)
	{
		file_dir = new String(filedir);
		address = requestPacket.getAddress();
		send_port = requestPacket.getPort();
		
		LSBlock = 0;
		MSBlock = 0;
		
		
		// Initialise the send and receive socket, if exception then quit
		try { send_receive_socket = new DatagramSocket(); }
		
		catch (SocketException se) { System.exit(1); }
		
	}
	
	
	/**
	 * Constructor for the Reader Class.
	 * 
	 * @param filedir   String containing the directory along with the file name 
	 *                  in the form (\directory\file_name.txt)
	 *                  
	 * @param requestPacket  DatagramPacket containing the port number and local
	 *                       address of the machine receiving the data sent from
	 *                       Reader
	 *                       
	 * @param ds  DatagramSocket to send data and receive ack packets
	 *  
	 */

	public Reader(String filedir, DatagramPacket requestPacket, DatagramSocket ds) 
	{
		// call other constructor
		this(filedir, requestPacket);
		
		// set the socket to ds
		send_receive_socket = ds;
	}
	
	
	/**
	 * Run method used by the start method to run the thread
	 * 
	 */
	
	public void run()
	{
		this.read_file();
	}
	
	
	/**
	 * Reads the file in 512 byte blocks and sends the data to
	 * the appropriate port and local address of the appropriate
	 * end user.
	 * 
	 */
	
	public void read_file()
	{
		
        byte[] data = new byte[512],       // byte array to be used for data of max size 512 bytes
               ackData = new byte[50];     // acknowledgement byte array to hold acknowledgement data 
        
        int m = 0;                         // 
        
        DatagramPacket sendPacket,                                                    // datagram packet for sending data
		               ackPacket = new DatagramPacket(ackData, ackData.length);       // datagram packet for receiving acknowledgements
		
		
		try 
		{ 
			// output message for user
			System.out.println("Opening " + file_dir + " to read.");
			
			// create BufferedInputStream instance
			BufferedInputStream in = new BufferedInputStream(new FileInputStream((String)file_dir)); 
			
			// number of bytes read
			int n = in.read(data, 0, data.length);                
			
			// create and initialize prevLSBlocks, prevMSBlocks representing the previous LSBlock and MSBlock numbers
			byte prevLSBlock, prevMSBlock;
			prevLSBlock = prevMSBlock = 0;
			
			// set boolean value for timeout
			boolean timeOut = false,
					error = false;
			
			// read until end of file
			while (n != -1)
			{
				// check if LSBlock is maxed out
				if(LSBlock == 127) 
				{
					// inc MSBlock
					MSBlock++;
					
					// reset LSBlock
					LSBlock = 0;
				} 
				
				// inc LSBlock
				else { LSBlock++; } 
					
				// set timeout to false
				timeOut = false;
				
				// create message byte array
				byte[] message = create_message(data);
				
				// create datagram packet containing the read data array, the size of array, local address and port number
				sendPacket = new DatagramPacket(message, message.length, address, send_port);
				
		    	// System.out.println("Sending packet containing data: ");
		    	// System.out.println(new String(message));
				
				// find how much data was sent
		    	m = Assistant.lengthFilled(message) - 4;
				System.out.println("Sent data was filled up " + m + " bytes.");
		    	
				// send the sendPacket through send_receive socket
		    	send_receive_socket.send(sendPacket);       
		    	
		    	// output message
		    	System.out.println("Waiting to receive ACK packet with block# = " + Assistant.printBlock(MSBlock, LSBlock));
		    	
		    	// try to wait to receive an acknowledgment packet
		    	try 
		    	{
		    		send_receive_socket.setSoTimeout(TIME_OUT_SECONDS * 1000);
		    		send_receive_socket.receive(ackPacket);     
		    	} 
		    	
		    	// catch exception
		    	catch (SocketTimeoutException ste) 
		    	{
		    		// output message
		    		System.out.println("ACK packet not received in time. Resending.");
		    		
		    		// set timeout to true
		    		timeOut = true;
		    		
		    		// dec LSBlock
		    	    LSBlock--;
		    		
		    	}
		    	
		    	// verify ackpacket received
		    	int e = Assistant.asExpected(ackPacket, (byte) 4, MSBlock, LSBlock);

		    	
		    	// if error output message, send error packet and terminate connection
		    	if(e != 0) 
		    	{
		    		// check type of error and output message
		    		if (e == 1) { System.out.println("Data corrupted: Invalid opcode"); }
		    			
		    		else if (e == 2) { System.out.println("Data corrupted: Invalid block number"); }
		    			
		    		else if (e == 3) { System.out.println("Data corrupted: Invalid opcode and block number"); }
		    			
		    		// send error packet
		    		Assistant.sendErrorPacket((byte)4, send_receive_socket, ackPacket);
		    		
		    		// set n to -1 to indicate EOF to terminate the connection
		    		n = -1;
		    		
		    		// set boolean error to true
		    		error = true;
		    	}
		    	
		    	
		    	// check if the port and/or address sending and receiving from are not equal
		    	if(ackPacket.getPort() != send_port || !(ackPacket.getAddress().equals(address))) 
		    	{
		    		// out message
		    		System.out.println("Packet received from unexpected source.");
		    		System.out.println("Sending error packet to that source.");
		    		
		    		
		    		// if port number is initialied and address is not null
		    		if (ackPacket.getPort() != -1 && ackPacket.getAddress() != null)
		    		{
		    			// send error packet
		    			Assistant.sendErrorPacket((byte) 5, send_receive_socket, ackPacket);
		    			
		    			// set timeout of socket to inf and wait to receive
		    		    send_receive_socket.setSoTimeout(0);
		    		    send_receive_socket.receive(ackPacket);
		    		}
		    				    		
		    	}
		    	
		    	//  set prevLSBlock and prevMSBlock
		    	if(LSBlock == 0) 
		    	{	
		    		prevLSBlock = 127; 
		    		prevMSBlock = (byte) (MSBlock - 1);
		    	} 
		    	
		    	else 
		    	{
		    		prevLSBlock = (byte) (LSBlock - 1);
		    		prevMSBlock = (byte) MSBlock;
		    	}
		    	
		    	
		    	// check if received and error, output message and terminating
		    	if(Assistant.isErrorPacket(ackPacket)) 
		    	{
		    		error = true;
		    	  	n = -1;
		    	  	System.out.println("Connection terminated due to error.");
		    	}
		    	
		    	
		    	// check if duplicated or lost ackpackets
		    	else if((isAcknowledged(ackPacket, MSBlock, LSBlock) && !timeOut) || isAcknowledged(ackPacket, prevMSBlock, prevLSBlock)) 
		    	{
		    		
		    		// checks if previous 
		    		if(isAcknowledged(ackPacket, prevMSBlock, prevLSBlock)) 
		    		{
		    			// output message
			    		System.out.println("Detected delayed ACK packet with block# = " + Assistant.printBlock(prevMSBlock, prevLSBlock));
			    		
			    		// set socket timeout to inf
			    		send_receive_socket.setSoTimeout(0);
			    		
			    		// output message
			    		System.out.println("Waiting to receive ACK packet with block# = " + Assistant.printBlock(MSBlock, LSBlock));
			    		
			    		// wait to receive and ackPacket
			    		send_receive_socket.receive(ackPacket);
			    		
			    	}
		    		
		    		// output message
		    		System.out.println("Received ACK packet with block# = " + Assistant.printBlock(ackPacket.getData()[2], ackPacket.getData()[3]));
		    		
		    		// create new byte array to be read
		    		data = new byte[512];                    
		    		
		    		// number of bytes read
			    	n = in.read(data, 0, data.length);  
			    	
			    	// get the port it came from
			    	send_port = ackPacket.getPort();
		    	}
		    	
		    	// handle files with exact multiples of 512 bytes
		    	if(m == 512 && n == -1 && !error) { n = 0; }
		    		
		    	
			}
			
			// closing the read stream
			in.close();                  
		}
		
		// catch file not found exceptions
		catch (FileNotFoundException fe)
		{
			// create instance of File
			File f = new File(file_dir);
			
			// check if it does not exist
			if (!f.exists())
			{
				// output message
				System.out.println("File was not found.");
				
				// send error packet
				Assistant.sendErrorPacket((byte)1, send_receive_socket, send_port, address);
			}
			
			// if it does exists
			else 
			{ 
				// output message
				System.out.println("File access is denied."); 
				
				// send error packet
				Assistant.sendErrorPacket((byte)2, send_receive_socket, send_port, address);
			}
			
		}
		
		
		// catch Socket Exceptions and output message
		catch (SocketException se) { System.out.println("Error in sending and receiving data."); }

		
		// catch IO Exceptions and out put message 
		catch (IOException ie) { System.out.println("I/O unimplemented error."); }

		
		// catch Exceptions that are not handled and output message
		catch (Exception e) { System.out.println("Unimplemented general error detected."); }

		
		// closes the socket and outputs message
		finally
		{
			//send_receive_socket.close();
			System.out.println("Reader thread terminated.");
		}
				
        
	}

	
	/**
	 * Creates a message to be send containing opcode, block number and 
	 * data read in from file specified by Client.
	 * 
	 * @param data  byte array containing the data read in from file
	 * 
	 * @return byte array containing an opcode, block number and data parameter
	 * 
	 */

	private byte[] create_message(byte[] data) 
	{
		byte[] message = new byte[data.length + 4];
		
		message[0] = 0;
		message[1] = 3;
		
		message[2] = MSBlock;
		message[3] = LSBlock;
		
		System.arraycopy(data, 0, message, 4, data.length);

		return message;
	}


	/**
	 * Verifies if the ackPacket received is the correct one.
	 * 
	 * @param ackPacket  DatagramPacket containing the acknowledgement information
	 * @param block  byte representing the block number corresponding to the number
	 *               of data sent
	 *               
	 * @return  boolean value representing whether or not the ackPacket is correct 
	 * 
	 */
	
	private boolean isAcknowledged(DatagramPacket ackPacket, byte b1, byte b0)
	{
		byte[] format = {0, 4, b1, b0};

		byte[] trimData = new byte[4];
		System.arraycopy(ackPacket.getData(), 0, trimData, 0, trimData.length);
		
		
		return equals(format, trimData);
	}
	
	
	
	/**
	 * Verifies that individual bytes received and expected match
	 * 
	 * @param a  byte array to be compared to
	 * @param b  byte array to compare parameter a to.
	 * 
	 * @return  boolean value representing whether or not the ackPacket is correct.
	 * 
	 */
	
	private static boolean equals(byte[] a, byte[] b) 
	{
		
		// checks length if not equal then return false
		if(a.length != b.length) { return false; }         
		
		// checks individual bytes if not equal then return false
		for(int i = 0; i < a.length; i++) 
		{
			if(a[i] != b[i]) { return false; }            
		}
		
		// return true if every thing checks out
		return true;                                     
	}
	
}
