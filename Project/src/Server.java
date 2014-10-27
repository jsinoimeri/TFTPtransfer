import java.io.*;
import java.net.*;

public class Server extends Thread {
	
	private DatagramSocket receiveSocket;
	private DatagramPacket receivePacket;
	private final int RECEIVE_PORT = 69;
	
	public Server(){
		try {
			receiveSocket = new DatagramSocket(RECEIVE_PORT);
		} catch (SocketException se) {
			System.out.println("Could not create datagram socket with port = " + RECEIVE_PORT);
			System.exit(1);
		}
	}
	
	public void run() {
	  
	    boolean keepRunning = true;
	    while(keepRunning){ 
	  
			// creating byte array and packet to receive requests
			byte[] data = new byte[512];
	        receivePacket = new DatagramPacket(data, data.length);

	        try {
	        	System.out.println("Waiting to receive requests...");
	        	receiveSocket.receive(receivePacket);
	        	System.out.println("Received packet containing: " + new String(data));
	        } catch (IOException e) {
		   		e.printStackTrace();
		   		System.exit(1);
    		}
	        
	        /*
	         * If the request is a valid read or write request, the 
	         * corresponding thread is created and started to deal with
	         * data transmission. Otherwise, if the first two bytes
	         * of the request are 0, the server shuts down.
	         */
	        if (isValidRequest(receivePacket)){
	        	
	        	System.out.print("The request received is valid and ");
	    	   // checks and specifies if its is a RRQ or WRQ
	    	   if (data[1] == 2){
	    		   System.out.println("it is a write request.");
	    		   try {
	    			   WRQresponse();
	    		   } catch (SocketException e) {
	    			   System.out.println("Port not allocated to socket.");
	    			   e.printStackTrace();
	    		   }
	    	   } else if (data[1] == 1) {
	    		   System.out.println("it is a read request.");
	    		   try {
	    			   RRQresponse();
	    		   } catch (IOException e) {
	    			   e.printStackTrace();
	    		   }
	    	   }
	        
	       } else if (data[0] == 0 && data[1] == 0) {
	    	   keepRunning = false;
	    	   
	       } else if(!isValidRequest(receivePacket)) {
	    	   System.out.println("The request is invalid.");
	    	   Assistant.sendErrorPacket((byte) 4, receiveSocket, receivePacket);
	       }
	       
	    } // end while
	    
	    System.out.println("Shut down instructions received from 'ControlServer'. \nShutting server down. Good bye world!");
		
	}
	
	/**
	 * this method responds to a WRQ
	 * @throws SocketException
	 */
	public void WRQresponse() throws SocketException{
		
		boolean startThread = true;
		Thread writer = null;
		
		String fileName = Assistant.extractFileName(receivePacket.getData());
		System.out.println("The mode is " + Assistant.extractModeName(receivePacket.getData()));
		try{
		    writer = new Writer(fileName, receivePacket.getPort(), receivePacket.getAddress());
		}
		catch(FileNotFoundException fe) { 
			
			startThread = false; 
		}
		
		finally{
			if (startThread) {
				System.out.println("Creating writer thread to deal with WRQ.");
				writer.start();
			}
			
		}
	}
		
	/**
	 * this method responds to a RRQ
	 * @throws SocketException
	 */
	public void RRQresponse() throws IOException {
		
		String fileName = Assistant.extractFileName(receivePacket.getData());
		Thread reader = new Reader(fileName, receivePacket);
		System.out.println("Creating reader thread to deal with RRQ.");
		reader.start();
	}
	
	/**
	 * checks if the request is valid
	 * @param aPacket - packet to be checked for validity
	 * @return - returns true if it is valid
	 */
	public boolean isValidRequest(DatagramPacket aPacket){

		byte data[] = aPacket.getData();
		String mode = Assistant.extractModeName(aPacket.getData());
		if ((data[0] == 0 && (data[1] == 1 || data[1] == 2)) && (mode.equals("netascii") || mode.equals("octet")))
			return true;
		
		return false;
	}
	
	public static void main(String[] args) {
		
		Server s = new Server();
		s.start();
	}
		
}


