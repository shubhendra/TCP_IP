/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet TCP manager</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */

import java.util.HashMap;
public class TCPManager {
    private Node node;
    private int Address;
    private Manager manager;
    private TCPSockSpace sockSpace;
    private static final byte empty[] = new byte[0];
    private static final int TRANSPORT_PKT = 4;
    
    public TCPManager(Node node, int Address, Manager manager) {
        this.node = node;
        this.Address = Address;
        this.manager = manager;
	this.sockSpace = new TCPSockSpace();

    }

    /**
     * Start this TCP manager
     */
    public void start() {
	

    }

/*
    public String createKey(int destPort, int destAdd, int srcPort, int srcAdd){
	return destAdd+"-"+destPort+"-"+srcAdd+"-"+srcPort;

    }

    public String[] parseKey(String key){
	String[] keySplits = key.split("[-]");
	return keySplits;
    }
*/
    /*
     * Begin socket API
     */

    /**
     * Create a socket
     *
     * @return TCPSock the newly created socket, which is not yet bound to
     *                 a local port
     */
    public TCPSock socket() {
	final TCPSock sock = this.sockSpace.newSocket();
		if (sock!=null){
			sock.setManager(this);
			TCPConnectionSocketIdentity id = sock.getID();
			id.setLocalAddress(this.Address);	
		}
        return sock;
    }

    public TCPSock getSocket(final int localAddress, final int localPort, final int remoteAddress, final int remotePort) {
        return this.sockSpace.getSocket(localAddress, localPort, remoteAddress, remotePort);
    }
    
    public TCPSock getSocket(final int localAddress, final int localPort, final TCPSock.State state) {
        return this.sockSpace.getLocalSocket(localAddress, localPort, state);
    }

    public TCPSock getSocket(final int localAddress, final int localPort) {
        return this.sockSpace.getLocalSocket(localAddress, localPort);

   }

    public void release(final TCPSock sock) {
        this.sockSpace.release(sock);
    }




   public void OnReceive(final int srcAddress, final int destAddress, final Transport segment) {
	final int destPort = segment.getDestPort();        
	final int srcPort = segment.getSrcPort();
        
        TCPSock sock;
        if (segment.getType() == 0) {
            sock = this.getSocket(destAddress, destPort, TCPSock.State.LISTEN);
            if (sock != null) {
                sock.OnReceive(srcAddress, destAddress, segment);
            }
        }
        else { 
        	sock = this.getSocket(destAddress, destPort, srcAddress, srcPort);
        	if (sock != null) {
        		sock.OnReceive(srcAddress, destAddress, segment);
        	return;
        	}
        }
        
    }

    public void send(final TCPConnectionSocketIdentity id, final int type, final int window, final int seq, final byte[] snd_buf, final int len) {
        byte[] payload;
        if (len > 0) {
            payload = new byte[len];
            TCPSock.readCopy(snd_buf, seq, payload, 0, len);
        }
        else {
            payload = TCPManager.empty;
        }
        final Transport segment = new Transport(id.getLocalPort(), id.getRemotePort(), type, window, seq, payload);
        this.node.sendSegment(id.getLocalAddress(), id.getRemoteAddress(), TRANSPORT_PKT, segment.pack());
    }

  

     public void addTimer(final long deltaT, final Callback callback) {
        this.manager.addTimerAt(this.Address, this.manager.now() + deltaT, callback);
    }
    /*
     * End Socket API
     */

	public long currentTime() {
		// TODO Auto-generated method stub
		return this.manager.now(); 
	}
}
