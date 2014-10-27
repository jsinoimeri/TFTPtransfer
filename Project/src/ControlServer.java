import java.io.*;
import java.net.*;

public class ControlServer extends Thread {
	
	DatagramSocket sendSocket;
	DatagramPacket sendPacket;
	private final int SEND_PORT = 69;
	
	public ControlServer() throws SocketException{
		
		sendSocket = new DatagramSocket();
	}
	
	public void run() {
		
		try {
			userOrders();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void userOrders() throws IOException{
		
		// message stating instructions to the user
		String popMsg = "Type 'shut' to shutdown server";
		String userChoice ="";
		
		// the data that will be sent to the server to shut it down
		byte[] data = {0 , 0};
		
		for(;;){
			System.out.println(popMsg);
			BufferedReader a = new BufferedReader(new InputStreamReader(System.in));
			try{
				userChoice = a.readLine();
			}catch (IOException e){
				e.printStackTrace();
			}
			
			if(userChoice.equalsIgnoreCase("shut")) {
				
				// prepare and send shutdown packet to the server
				sendPacket = new DatagramPacket(data, data.length, 
						InetAddress.getLocalHost(), SEND_PORT);
				sendSocket.send(sendPacket);
				break;
			}
		}
	}
	
	public static void main(String[] args) throws SocketException {
		
		Thread c = new ControlServer();
		c.start();
	}

}
