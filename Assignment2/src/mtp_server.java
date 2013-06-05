import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Random;


public class mtp_server {
	public static void main(String args[]) throws Exception {
		// check inputs
		if (args.length < 2) {
			System.out.println("Not enough arguements");
			return;
		}
		
		int myPort = Integer.parseInt(args[0]);
		String fileName = args[1];
		DatagramSocket socket = new DatagramSocket(myPort);
		int expectingSeqNum = 0;
		int serverSeqNum = 10;
//		int dataLength = 0;
		boolean finished = false;
//		Packet previousPacket = null;
		Random rand = new Random();
		server_isn = rand.nextInt();
		int previousAck = 0;
		
		File file = new File(fileName);
		FileOutputStream fos = new FileOutputStream(file);
		
		if (!file.exists())
			file.createNewFile();
		
		Packet ackReply = null;
		
		// run forever
		while (true) {
			DatagramPacket request = new DatagramPacket(new byte[FILE_SIZE], FILE_SIZE);
			socket.receive(request);
			System.out.println("Received data");
			
			Packet p = Serialisation.deserialise(request);
			
			// if packet received is a SYN, establish a connection.
			if (p.isSYN()) {
				System.out.println("SYN packet received");
				establishConnection(p, socket, request.getAddress(), request.getPort());
			}
			
			// if packet received is a FIN, establish a connection.
			if (p.isFIN()) {
				System.out.println("FIN received");
				closeConnection(socket);
			}
			
			if (connectionEstablished && finished == false) {
				if (p.getData() != null && p.getData().length > 0) {
//					if (previousPacket != null)
//						dataLength = previousPacket.getData().length;
//					else
//						dataLength = p.getData().length;
//					previousPacket = p;
					System.out.println("Packet with seq # " + p.getSeqNumber() + " received");
					ackReply = new Packet(serverSeqNum);
					ackReply.setACK(true);
					System.out.println(p.getAckNumber());
					// if the packet received is in the correct order, then write to file.
					if (p.getSeqNumber() == expectingSeqNum) {
						fos.write(p.getData());
						fos.flush();
//						System.out.println(new String(p.getData()));
						
						previousAck = expectingSeqNum;
						
						// check if there were any out of order packets
						// that were in sequence with the packet just received.
						// set expectingSeqNum accordingly.
						expectingSeqNum = getBufferedData(expectingSeqNum, fos);
						if (expectingSeqNum == previousAck)
							expectingSeqNum = p.getAckNumber();
						System.out.println("Expecting Seq #: " + expectingSeqNum);
						
						// send ACK to client
						ackReply.setAckNumber(expectingSeqNum);
						
						socket.send(Serialisation.serialise(ackReply, clientAddress, clientPort));
						System.out.println("Ack number " + ackReply.getAckNumber() + " sent");
						receivedCorrectly.add(p);
						
						
					} else {
						if (hasPacket(p, receivedCorrectly) == null) {
							System.out.println("Out of order. Packet is buffered");
							outOfOrder.add(p);
							ackReply.setAckNumber(expectingSeqNum);
							socket.send(Serialisation.serialise(ackReply, clientAddress, clientPort));
							System.out.println("Ack # " + ackReply.getAckNumber() + " resent");
						} else {
							System.out.println("Packet received before");
							ackReply.setAckNumber(expectingSeqNum);
							socket.send(Serialisation.serialise(ackReply, clientAddress, clientPort));
						}
					}
				}
			}
			
			// if the connection is no longer in established state
			// exit the program
			if (connectionEstablished == false) {
				System.out.println("Exiting program");
				fos.close();
				System.exit(0);
			}
		}
		
	}
	
	public static void establishConnection(Packet requestPacket, DatagramSocket socket, InetAddress client, int port) throws Exception {
		reply = new Packet(server_isn);
		reply.setACK(true);
		reply.setSYN(true);
		reply.setAckNumber(requestPacket.getSeqNumber() + 1);
		
		// send SYNACK
		socket.send(Serialisation.serialise(reply, client, port));
		System.out.println("SYNACK sent");
		
		try {
			// try to receive an ACK for SYNACK
			DatagramPacket responseFromClient = new DatagramPacket(new byte[FILE_SIZE], FILE_SIZE);
			socket.receive(responseFromClient);
			
			Packet p = Serialisation.deserialise(responseFromClient);
			if (p.isACK()) {
				// ACK received, establish a connection
				System.out.println("ACK for SYNACK received");
				clientAddress = responseFromClient.getAddress();
				clientPort = responseFromClient.getPort();
				connectionEstablished = true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void closeConnection(DatagramSocket socket) throws Exception {
		System.out.println("Closing connection");
		reply = new Packet(server_isn);
		reply.setACK(true);
		socket.send(Serialisation.serialise(reply, clientAddress, clientPort));
		System.out.println("Sent ACK for FIN");
		
		reply.setACK(false);
		reply.setFIN(true);
		socket.send(Serialisation.serialise(reply, clientAddress, clientPort));
		System.out.println("Sent FIN from server");
		
		try {
			DatagramPacket response = new DatagramPacket(new byte[FILE_SIZE], FILE_SIZE);
			socket.receive(response);
			
			Packet p = Serialisation.deserialise(response);
			if (p.isACK()) {
				System.out.println("Received ACK for FIN");
				clientAddress = null;
				clientPort = '0';
				connectionEstablished = false;
				outOfOrder.clear();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static int getBufferedData(int expectingSeqNum, FileOutputStream fos) throws IOException {
//		int temp = expectingSeqNum;
//		for (int i = 0; i < dataLength; i++) {
//			temp++;
//			Packet tempPacket = new Packet(temp);
//			tempPacket = hasPacket(tempPacket, outOfOrder);
//			if (tempPacket != null)
//				return tempPacket;
//		}
//		return null;
		
		for (Packet p : outOfOrder) {
			if (hasPacket(p, receivedCorrectly) == null) {
				if (p.getSeqNumber() <= expectingSeqNum) {
					System.out.println("Line 187: " + p.getAckNumber());
					fos.write(p.getData());
					fos.flush();
					expectingSeqNum = p.getAckNumber();
					System.out.println("Expecting seq # line 192: " + expectingSeqNum);
					receivedCorrectly.add(p);
				}
			}
		}
		return expectingSeqNum;
	}
	
	private static Packet hasPacket(Packet packet, LinkedList<Packet> list) {
		for (Packet p : list) {
			if (p.getSeqNumber() == packet.getSeqNumber())
				return p;
		}
		return null;
	}
	
	private static Packet reply;
	private static InetAddress clientAddress;
	private static int clientPort;
	private static boolean connectionEstablished = false;
	private static int server_isn;
	private static final int FILE_SIZE = 1024 * 5;
	private static final int TIME_OUT = 3000;
	private static LinkedList<Packet> outOfOrder = new LinkedList<Packet>();
	private static LinkedList<Packet> receivedCorrectly = new LinkedList<Packet>();
}
