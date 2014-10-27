/**
 * Client 
 * 
 * @author Jeton Sinoimeri
 * 
 * @created May 11, 2014
 * @modified June 15, 2014
 * 
 */

import java.io.*;
import java.net.*;
import java.util.Scanner;


public class Client 
{

    private static final int TIMEOUTSEC = 60,          // time out for client socket to receive something back from server
    		                 SEND_ERRROR = 68,         // port number for error simulator
    		                 SEND_SERVER = 69,         // port number for server
    		                 MESSAGEBYTEARRAY = 100;   // the max length of the message to send to server
    
    
    private DatagramPacket sendPacket,                 // Datagram Packet for sending
                           receivePacket;              // Datagram Packet for receiving
    
    
    private DatagramSocket sendReceiveSocket;          // Datagram Socket for sending and receiving
    
    private byte[] message;                            // byte array for sending the initial message
    
    private int send_port;                             // int representing the port number to send data
    
    
    private String rw_req,                             // String representing read/write request inputed by user 
                   write_file_dir,                     // String representing the path and name of file to be written
                   read_file_dir,                      // String representing the path and name of file to be read
                   mode;                               // String representing the mode either test or normal
    
    
    
    
    
    /**
     * Constructor for the Client class
     * 
     */
    
    public Client() 
    {    	
    	// try to create socket if error then exit
    	try 
    	{ 
    		sendReceiveSocket = new DatagramSocket(); 
    	}
    	catch (SocketException se) 
    	{ 
    		System.exit(1); 
    	}
    }

    
    /**
     * Creating the initial message to be sent to 
     * the server in the TFTP format.
     *  
     */
    
    private void create_inital_message()
    {
    	
    	// create byte array
    	message = new byte[MESSAGEBYTEARRAY];
    	
    	// byte array for the path and name of file
	    byte[] fdir = null;                         
	    message[0] = 0;
	    
	    
	    // read request
	    if (this.rw_req.equals("read")) 
	    {	
	    	this.message[1] = 1;
	    	fdir = this.read_file_dir.getBytes();
	    }
	    
	    // write request
	    else if (rw_req.equals("write")) 
	    {
	    	message[1] = 2; 
	    	fdir = write_file_dir.getBytes();
	    }
	    
	    // copy into the message
	    System.arraycopy(fdir, 0, message, 2, fdir.length);
	    message[fdir.length+2] = 0;
	   
	    // convert to bytes
      byte [] md = "netascii".getBytes();
       
      // copy into the message
      System.arraycopy(md, 0, message, fdir.length+3, md.length);
        
       
      // length of the message
      int len = fdir.length + md.length+4;             
        
      // end with another 0 byte 
      message[len-1] = 0;
        
    }
    
    
    /**
     * Creates the corresponding acknowledgment array to the request sent
     * to the server.
     * 
     * @return byte array representing the acknowledgment array for
     *         the corresponding request
     * 
     */
    
    private byte[] create_ack() 
    {
    	// byte array
    	byte[] ack = new byte[4];
    	
    	// read request
    	if (rw_req.equals("read")) 
    	{
    		ack[0] = 0;
    		ack[1] = 3;
    		ack[2] = 0;
    		ack[3] = 1;
    	}
    	
    	// write request
    	else if (rw_req.equals("write")) 
    	{	
    		ack[0] = 0;
    		ack[1] = 4;
    		ack[2] = 0;
    		ack[3] = 0;
    	}
    	
    	// return byte array
    	return ack;
    }
    
    
    /**
     * Verifies that the acknowledgment packet sent by server is correct.
     * 
     * @param ack_r   byte array received from server
     * @param ack_exp  byte array expected from server created by
     *                 create_ack() method.
     *                 
     * @return boolean value representing if both packets are equal
     * 
     */
    
    private boolean ack_verified(byte[] ack_r, byte[] ack_exp)
    {
    	
    	// checks length if not equal then return false
      if(ack_r.length != ack_exp.length) { return false; }    
		
	        // checks individual bytes if not equal then return false
			for(int i = 0; i < ack_r.length; i++) 
			{
				if(ack_r[i] != ack_exp[i])
				   return false;            
			}
		
		   // return true if every thing checks out
		   return true;                                     
    }
    
    
    /**
     * Sends and Receives requests and data.
     * 
     */
    
    public void sendAndReceive()
    {
    	

    	// scanner instance for user input
      Scanner input = new Scanner(System.in);               
        
      // boolean variable to quit the client
      boolean quit = false;                                
        
      // ask user for mode and server IP
      InetAddress serverIP = changeModeIP(input);           
        
        
        // loops until user enters quit
        while (!quit)
        {	
        	
        	// get user input to read, write, change mode or quit
        	System.out.println("Enter read, write, change or quit: ");                   
            rw_req = input.nextLine();
            
            // check if invalid option
            while (!(rw_req.equals("quit") || rw_req.equals("read") || rw_req.equals("write") || rw_req.equals("change")))
            {
            	System.out.println("Invalid option. Re-enter read, write, change or quit: ");
                rw_req = input.nextLine();
            }
      	            
            
            // check if user hasn't typed quit
            if (!rw_req.equals("quit"))
            {       	
                try 
                {
                	// check if user has inputed read, or write
                	if (!rw_req.equals("change"))
                	{
                		// get user input for file to be read
	                	System.out.println("Enter directory and file name to be read: ");
	                    this.read_file_dir = input.nextLine();
	                    
	                    // get user input for file to be written
	                    System.out.println("Enter directory and file name to be written: ");
	                    this.write_file_dir = input.nextLine();
	                    
	                    
	                    // create the appropriate message
	                    create_inital_message();
	            		
	                    // create the packet and send the packet
	                	sendPacket = new DatagramPacket(message, message.length, serverIP, send_port);
	                	sendReceiveSocket.send(sendPacket);
	                	
	                	// set the socket timeout
	                	sendReceiveSocket.setSoTimeout(TIMEOUTSEC * 1000);
                	}
                	
                	// output mode and IP address
                	System.out.println("\nMode: " + mode +"\nIP Address: " + serverIP + "\n");
            		
                	
                	// write requests
                	if (this.rw_req.equals("write")) 
            		{ 
                		// create byte array for expected ack
                		byte[] ack_expected = this.create_ack();
                    	
                		// create byte array for received ack
                    	byte[] ack_received = new byte[35];                      
                    	
                    	
                    	// create packet and receive ack packet
                    	this.receivePacket = new DatagramPacket(ack_received, ack_received.length);
                    	this.sendReceiveSocket.receive(this.receivePacket);
                    	
                    	// check if it is an error
                    	if(receivePacket.getData()[1] == 5) 
                    	{
                    		/// display error message
                			System.out.println(Assistant.extractErrorMessage(receivePacket.getData()));
                    			
                    		// throw an IO Exception to terminate the current transaction
                			throw new IOException();
                		}
                    	
                    	// trim the ack packet to 4 bytes
                    	ack_received = Assistant.trimArrayToAck(receivePacket);
                    	

                    	// verify the ack packet and start reading the file
                    	if (this.ack_verified(ack_expected, ack_received)) 
                    	{ 
                			Reader reader = new Reader(this.read_file_dir, this.receivePacket, sendReceiveSocket); 
                			reader.read_file();
                		} 
                    	
                    	// if error then output message
                    	else { System.out.println("Connection could not be established. Error occured on the server end."); }
                			
            		} 
                	
                	// read request
                	else if (this.rw_req.equals("read"))
                	{
                		// create byte array
                		byte[] data = new byte[516];                               
                		
                		// create and receive data
                		this.receivePacket = new DatagramPacket(data, data.length);
                		System.out.println("Waiting to receive data packet with block# = 1");
                		this.sendReceiveSocket.receive(receivePacket);
                		
                		// check if error packet
                		if(receivePacket.getData()[1] == 5) 
                		{
                			// display error message
                			System.out.println(Assistant.extractErrorMessage(receivePacket.getData()));
                    			
                			// throw IO Exception to terminate the current transaction
                			throw new IOException();
                		}
                		
                		// output the block number received
                		System.out.println("Received data packet with block# = " + data[3]);
                		// System.out.println("containing data:\n " + new String(data));
                		
                		// try to write to file
                		try 
                		{
                			Writer write = new Writer(this.write_file_dir, this.receivePacket.getPort(), receivePacket.getAddress(), data, sendReceiveSocket);
                			System.out.println("Switching to sendAndReceive() in Writer class.");
                    		write.sendAndReceive();	
                		}
                		
                		// if File not found exception thrown, output file already exists
                		catch(FileNotFoundException fe) { System.out.println("File already exists. Cannot overwrite.");   }

                	}
                	
                	// change mode and IP
                	else if (this.rw_req.equals("change")) { serverIP = changeModeIP(input); }
                	
                }
            	
                // catch IO Exceptions and output message
            	catch (IOException e) { System.out.println("There was an error during transaction. Please try again."); }

            } 
            
            // set boolean value to true to terminate client
            else { quit = true; }
        }
    	
        // close socket and scanner instance
        this.sendReceiveSocket.close();
        input.close();
    	
        // output message
    	System.out.println("Client has been terminated.");
    	
    }
    
    
    /**
     * Changes the mode and IP address of the server.
     * 
     * @param ui  Scanner instance used to get input from user
     * 
     * @return InetAddress representing the server IP. If test mode chosen then local IP, else server IP.
     * 
     */
    
    private InetAddress changeModeIP(Scanner ui)
    {
    	// create InetAddress instance and initialize to null
    	InetAddress serverIP = null;
    	
    	// output messages for choosing the mode
    	System.out.println("Choose the mode: 'test' or 'normal'.\n  Test - sends requests to ErrorSimulator. If chosen then server IP will be asked in ErrorSimulator.\n  Normal - sends requests to Server. If chosen then you will be asked for server IP in the Client.");
    	System.out.println("\nEnter the mode you want to select: ");
    	
    	// get user input
    	this.mode = ui.nextLine();
    	
    	// check if its valid, if not ask again until valid
    	while (!(mode.equals("test") || mode.equals("normal")))
    	{
    		System.out.println("Invalid option. Please try again.");
    		System.out.println("Choose the mode: test or normal.\n  Test - sends requests to ErrorSimulator. If chosen then server IP will be asked in ErrorSimulator.\n  Normal - sends requests to Server. If chosen then you will be asked for server IP in the Client.");
        	System.out.println("\nEnter the mode you want to select: ");
        	
        	this.mode = ui.nextLine();
    	}
    	
    	
    	// check if test is chosen
    	if(mode.equals("test"))
    	{
    		// try to get local host Address
    		try { serverIP = InetAddress.getLocalHost(); }
        	
    		// output unknown host if cannot
        	catch (UnknownHostException uhe) { System.out.println("Unknown host"); }
    		
    		// set the port to error simulator port
    		send_port = SEND_ERRROR;
    	}
    	
    	// check if normal is chosen
    	else if (mode.equals("normal"))
    	{
    		
    		// keep trying to get valid server IP
	    	while (serverIP == null)
	    	{
	    		// output message to user
	    		System.out.println("Enter the IP of server: ");
	    		
	    		// try to get valid IP
	        	try { serverIP = InetAddress.getByName(ui.nextLine()); }
	        	
	        	// catch exception, output message
	        	catch (UnknownHostException uh) 
	        	{
	        		System.out.println("Unknown host");
	        		serverIP = null;
	        	}
	    	}
	    	
	    	// set the port number to server port
	    	send_port = SEND_SERVER;
    	}
       	 	
    	// return the server IP
    	return serverIP;
    }
    
    
    public static void main(String args[]) throws IOException
    {
       Client c = new Client();
       c.sendAndReceive();
    }

}
