import java.io.*;
import java.net.*;

public class Assistant {
	
	public static byte RRQ_OPCODE = 1;
	public static byte WRQ_OPCODE = 2;
	public static byte DATA_OPCODE = 3;
	public static byte ACK_OPCODE = 4;
	public static byte ERROR_OPCODE = 5;
	
	/**
	 * this method resizes a given byte array to a byte array
	 * with the specified size
	 * @param elongatedData - the byte array to be resized
	 * @param newSize - new size for the byte array to be resized
	 * @return - the resized byte array
	 */
	private static byte[] trimArray(byte[] elongatedData, int newSize) {
		
		byte[] trimmedData = new byte[newSize];
		System.arraycopy(elongatedData, 0, trimmedData, 0, trimmedData.length);
		
		return trimmedData;
	}
	
	/**
	 * this method uses the above trimArray() method to reduce the provided 
	 * packet's byte array to the size of an ACK byte array
	 * @param packet
	 * @return - returns the trimmed array
	 */
	public static byte[] trimArrayToAck(DatagramPacket packet) {
		
		int ackSize = 4;
		byte[] elongatedData = packet.getData();
		return trimArray(elongatedData, ackSize);
	}
	
	/**
	 * this method uses the above trimArray() method to reduce the provided 
	 * packet's byte array to the size of an ERROR packet
	 * @param packet
	 * @return - returns the resized array
	 */
	public static byte[] trimArrayToError(DatagramPacket packet) {
		
		int errorSize = 50;
		byte[] elongatedData = packet.getData();
		return trimArray(elongatedData, errorSize);
	}
	
	/**
	 * checks if the packet is an error packet
	 * @param packet - datagram packet to be checked
	 * @return - true if the packet is an error packet
	 */
	public static boolean isErrorPacket(DatagramPacket packet)
	{
		if (packet.getData()[1] == (byte) 5)
			return true;
		
		return false;
	}
	
	/**
	 * This method checks if the data in the datagram packet occurs as expected to be.
	 * 
	 * @param packet - datagram packet to be checked
	 * @param opCode - expected opcode value
	 * @param block - expected block#
	 * @return 1 - if unexpected opcode found
	 *         2 - if unexpected block# found
	 *         3 - if unexpected opcode and block# found
	 *         0 - if everything found was as expected
	 */
	public static int asExpected(DatagramPacket packet, byte opCode, byte b1, byte b0) {
		
		byte[] data = packet.getData();
		int ret = 0;
		int block = printBlock(b1, b0);
		
		
		if (block - 2 > 0)
		{
		
			if(data[1] != opCode) {
				ret = 1; 
			
			}
		
			if((printBlock(data[2], data[3]) > (block + 2) || printBlock(data[2], data[3]) < (block - 2)) && data[1] != 5) {
				ret = 2;
				
			}
		
			if(data[1] != opCode && ret == 2) {
				ret = 3;
			}
		}
		
		return ret;
	}
	
	public static void dealWithCorruption(int error, DatagramPacket packet)
	{
		if (error == 1) 
			System.out.println("Data corrupted: Invalid opcode");

		else if (error == 2)
			System.out.println("Data corrupted: Invalid block number");
		
		else if (error == 3)
			System.out.println("Data corrupted: Invalid opcode and block number");
	}
	
	/**
	 * @param b1 - MSB
	 * @param b0 - LSB
	 * @return - equivalent integer value
	 */
	public static int printBlock(byte b1, byte b0) {
		
		return (int) b1 * 128 + b0;
	}
	
	public static void sendErrorPacket(byte errorOpcode, DatagramSocket sendSocket, DatagramPacket packet) {
		
		sendErrorPacket(errorOpcode, sendSocket, packet.getPort(), packet.getAddress());
	}
	
	public static void sendErrorPacket (byte errorOpcode, DatagramSocket sendSocket, int destPort, InetAddress add){
		
		byte[] msgArray;
		String msg = "";
		int cursor = 0;
		
		//prepare the error message depending on the error code
		if (errorOpcode == (byte) 1){
			msg = "Error 1: File not found";
		}else if(errorOpcode == (byte) 2){
			msg = "Error 2: File can not be accessed";
		}else if(errorOpcode == (byte) 3){
			msg = "Error 3: Disk full";
		}else if (errorOpcode == (byte) 4){
			msg = "Error 4: Packet corrupted";
		}else if(errorOpcode == (byte) 5){
			msg = "Error 5: Duplicated request";
		}else if(errorOpcode == (byte) 6){
			msg = "Error 6: File already exists";
		}
		
		msgArray = msg.getBytes();
		
		// 2 bytes for the error opcode {0,5} then 2 bytes for the error type opcode {0,#} the msg and a byte for 0
		int len = 5 + msgArray.length;
		
		byte[] errorArray = new byte[len];
		//Constructing the array for the error packet
		errorArray[0] = 0;
		errorArray[1] = 5;
		errorArray[2] = 0;
		errorArray[3] = errorOpcode;
		cursor = 4; // current position in array
		
		//add error msg
		System.arraycopy(msgArray, 0, errorArray, cursor, msgArray.length);
		//update cursor
		cursor += msgArray.length;
		//add the last byte '0'
		errorArray[cursor] = 0;
		
		//create the packet to be sent
		try{
			DatagramPacket errorPacket = new DatagramPacket(errorArray, errorArray.length, add, destPort);
			try {
		        sendSocket.send(errorPacket);
			} catch(SocketException se) {
				System.out.println("Could not create socket to send error packet.");
			}
	        	
		} catch (IOException e) {
			
	         e.printStackTrace();
	         System.exit(1);
		}		
	}	  
		
	/**
	 * extracts the file name from the RRQ/WRQ packet
	 * @param data - byte array containing data
	 * @return - string value of name of file to be read/written to
	 */
	public static String extractFileName (byte[] data){
		
		byte[] fileNameInBytes = new byte[100];
				
		for(int i = 2; data[i] != 0b0; i++)
			fileNameInBytes[i-2] = data[i];
		
		System.out.println("The file name is " + new String(fileNameInBytes).trim());
		return new String(fileNameInBytes).trim();

	}
	
	/**
	 * extracts the error message from the ERROR packet
	 * @param data - byte array containing data
	 * @return - string value of error message
	 */
	public static String extractErrorMessage(byte[] data) {
		
		byte[] errorMessageInBytes = new byte[100];
		
		for(int i = 4; data[i] != 0b0; i++)
			errorMessageInBytes[i-4] = data[i];
		
		System.out.println(new String(errorMessageInBytes).trim());
		return new String(errorMessageInBytes).trim();
	}
	
	/**
	 * extracts the mode name from the RRQ/WRQ packet
	 * @param data - byte array containing data
	 * @return - string value of mode name
	 */
	public static String extractModeName (byte[] data) {
		
		byte[] modeNameInBytes = new byte[20];
		
		int mode = -1;
		for(int i = 2; data[i] != 0b0; i++)
			mode = i+2;
		
		if(mode != -1) {
			for(int i = 0; data[mode] != 0b0; i++) {
				modeNameInBytes[i] = data[mode];
				mode++;
			}
		}
		
		return new String(modeNameInBytes).trim();
	}
	
	/**
	 * @param data - byte array containing data
	 * @return the integer value of the length of the byte array 
	 *         upto which it has been filled with data
	 */
	public static int lengthFilled(byte[] data) {
		
		for(int i = 4; i < data.length; i++)
			if(data[i] == 0)
				return i;
		
		return data.length;
	}
	

}
