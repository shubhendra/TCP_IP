import java.util.ArrayList;
public class TCPSockSpace {
	ArrayList<TCPSock> sockArray;

	public TCPSockSpace(){
		sockArray = new ArrayList<TCPSock>();
	}
   	


	public TCPSock newSocket() {
		TCPSock newSock = new TCPSock();
		sockArray.add(newSock);
		return newSock;	     
	}
	

  	public TCPSock getLocalSocket(final int localAddress, final int localPort, final TCPSock.State state) {
        	for (int i = 0; i < this.sockArray.size(); i++) {
        	    TCPSock sock = this.sockArray.get(i);
        	    if (sock != null) {
        	        TCPConnectionSocketIdentity id = sock.getID();
        	        if (id.getLocalAddress() == localAddress && id.getLocalPort() == localPort && state == sock.getState()) {
        	            return sock;
        	        }
        	    }
        	}
        	return null;
    	}

	public TCPSock getLocalSocket(final int localAddress, final int localPort) {
        	for (int i = 0; i < this.sockArray.size(); i++) {
        	    TCPSock sock = this.sockArray.get(i);
        	    if (sock != null) {
        	        TCPConnectionSocketIdentity id = sock.getID();
        	        if (id.getLocalAddress() == localAddress && id.getLocalPort() == localPort) {
        	            return sock;
        	        }
        	    }
        	}
        	return null;
    	}

 	public TCPSock getSocket(final int localAddress, final int localPort, final int remoteAddress, final int remotePort) {
 
        	for (int i = 0; i < this.sockArray.size(); i++) {
        	    TCPSock sock = this.sockArray.get(i);
        	    if (sock != null) {
        	        TCPConnectionSocketIdentity id = sock.getID();
        	        if (id.getLocalAddress() == localAddress && id.getLocalPort() == localPort && id.getRemoteAddress() == remoteAddress && id.getRemotePort() == remotePort) {
        	            return sock;
        	        }
        	    }
        	}
        	return null;
 
 	   }

	public void release(final TCPSock sock) {
        	for (int i = 0; i < this.sockArray.size(); i++) {
        	    if (this.sockArray.get(i) == sock) {
        	        this.sockArray.remove(i);
        	        return;
        	    }
        	}
    	}

}
