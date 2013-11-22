/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet socket implementation</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.io.*;

public class TCPSock {
    // TCP socket states
    enum State {
        // protocol states
    	NEW,
        CLOSED,
        LISTEN,
        SYN_SENT,
        ESTABLISHED,
        SHUTDOWN // close requested, FIN not sent (due to unsent data in queue)
    }
    private State state;
    private TCPManager tcpManage;
    private TCPConnectionSocketIdentity id;
    private int senderInitSeqNum;
    private int receiverInitSeqNum;
    private byte[] receiverBuffer;
    private byte[] senderBuffer;
    private ArrayList<TCPSock> pendingBacklogConnections;
    private int backlog;
    private int senderBase;
    private int senderUpperLimit;
    private int senderNextSeqNum;
    private int receiverBase;
    private int receiverUpperLimit;
    private int receiverNextSeqNum;
    private int senderMSS = 100;
    private int duplicateACK;
    private int fastRetransmit;
    private int retransmitExpired;
    private int retransmitWaiting;
    private int senderWindow;
    private long RTT_estimate;
    private long RTTDev_estimate;
    private int RTT_sample_seq;
    private long RTT_sample_send_time;
    private long RTO;
    private int senderReceiveWindow;
    private float senderCwnd;
    private float senderThreshold;;

    public TCPSock() {
	this.state = State.NEW;
	this.tcpManage = null;
	this.id = new TCPConnectionSocketIdentity();
	this.pendingBacklogConnections = null;
	this.backlog = 0;
	this.senderBase = 0;
	this.senderUpperLimit = 0;
	this.senderNextSeqNum = 0;
	this.receiverBase = 0;
	this.receiverUpperLimit = 0;
	this.receiverNextSeqNum = 0;
	this.duplicateACK = 0;
    this.fastRetransmit = 0;
    this.retransmitExpired = 0;
    this.retransmitWaiting = 0;
    //this.senderWindow = 1000;
    this.RTTDev_estimate = 0;
    this.RTT_sample_seq = -1;
    this.RTT_sample_send_time = 0;
    this.RTO = 1000;
    this.RTT_estimate = 1000;
    this.senderReceiveWindow = 0;
    this.senderCwnd = 1.0f;
    this.senderThreshold = 16384.0f;
    this.updateSenderWindow();

    }

    public TCPManager getManager() {
        return this.tcpManage;
    }

    public void setManager(final TCPManager tcpManage) {
        this.tcpManage = tcpManage;
    }

    public TCPConnectionSocketIdentity getID() {
        return this.id;
    }
    public State getState() {
        return this.state;
    }
    /*
     * The following are the socket APIs of TCP transport service.
     * All APIs are NON-BLOCKING.
     */

    /**
     * Bind a socket to a local port
     *
     * @param localPort int local port number to bind the socket to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(int localPort) {
	if (this.id.getLocalPort() != -1) {
          	return -1;	
	}
	
	int localAddress = this.id.getLocalAddress();
	TCPSock sock = this.tcpManage.getSocket(localAddress, localPort);
	if (sock !=null) {
		return -1;
	}
	this.id.setLocalPort(localPort);
        return 0;
    }

    /**
     * Listen for connections on a socket
     * @param backlog int Maximum number of pending connections
     * @return int 0 on success, -1 otherwise
     */
    public int listen(int backlog) {
	if (this.state != State.NEW) {
            return -1;
        }
        if (this.id.getLocalPort() == -1) {
            return -1;
        }
        this.pendingBacklogConnections = new ArrayList<TCPSock>();
        this.backlog = backlog;
        this.state = State.LISTEN;
        return 0;

    }

    /**
     * Accept a connection on a socket
     *
     * @return TCPSock The first established connection on the request queue
     */
    public TCPSock accept() {
	if (this.state != State.LISTEN) {
            return null;
        }
        if (this.pendingBacklogConnections.isEmpty()) {
            return null;
        }
        return this.pendingBacklogConnections.remove(0);

    }

    public boolean isConnectionPending() {
        return (state == State.SYN_SENT);
    }

    public boolean isClosed() {
        return (state == State.CLOSED);
    }

    public boolean isConnected() {
        return (state == State.ESTABLISHED);
    }

    public boolean isClosurePending() {
        return (state == State.SHUTDOWN);
    }

    /**
     * Initiate connection to a remote socket
     *
     * @param destAddr int Destination node address
     * @param destPort int Destination port
     * @return int 0 on success, -1 otherwise
     */
    public int connect(int destAddress, int destPort) {
         if (this.state != State.NEW) {
            return -1;
        }
        if (this.id.getLocalPort() == -1) {
            return -1;
        }
        this.id.setRemoteAddress(destAddress);
        this.id.setRemotePort(destPort);
	//set up the sender buffers and variables
        this.senderBuffer = new byte[20000];
        int initSeq = 0;
        this.senderNextSeqNum = initSeq;
        this.senderUpperLimit = initSeq+1;
        this.senderBase = initSeq;
        this.senderInitSeqNum = initSeq;
        this.state = State.SYN_SENT;
        this.sendSYN();
        return 0;
    }

     public void sendSYN() {
        if (this.state != State.SYN_SENT) {
            return;
        }
        this.tcpManage.send(this.id, 0, 0, this.senderBase, this.senderBuffer, 0);
        try {
		//callback to send the SYN again in case it gets lost
            final Method method = Callback.getMethod("sendSYN", (Object)this, (String[])null);
            final Callback cb = new Callback(method, (Object)this, (Object[])null);
            this.tcpManage.addTimer(1000L, cb);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
	if (this.state == State.LISTEN) {
		//check if the server has any pending connections
            while (!this.pendingBacklogConnections.isEmpty()) {
                final TCPSock conn = this.pendingBacklogConnections.remove(0);
                this.tcpManage.release(conn);
            }
            this.state = State.CLOSED;
        }
        else if (this.state == State.ESTABLISHED) {
		//check if client has sent all the data
            if (this.senderBase == this.senderNextSeqNum) {
                this.tcpManage.send(this.id, 2, 0, this.senderBase, this.senderBuffer, 0);

                this.state = State.CLOSED;
            }
            else {
                this.state = State.SHUTDOWN;
            }
        }
        else if (this.state != State.SHUTDOWN) {
            this.state = State.CLOSED;
        }
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
	this.close();
        if (this.state == State.SHUTDOWN) {
            this.tcpManage.send(this.id, 2, 0, this.senderBase, this.senderBuffer, 0);
        }
        this.tcpManage.release(this);
    }

    /**
     * Write to the socket up to len bytes from the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer to write from
     * @param pos int starting position in buffer
     * @param len int number of bytes to write
     * @return int on success, the number of bytes written, which may be smaller
     *             than len; on failure, -1
     */
    public int write(byte[] buf, int pos, int length) {
         if (this.state == State.CLOSED) {
            return -1;
        }
        if (this.state != State.ESTABLISHED) {
            return -1;
        }
        if (this.senderBuffer == null) {
            return -1;
        }
        final int availableSpaceInBuffer = this.senderBuffer.length - 1 - (this.senderNextSeqNum - this.senderBase);
        final int countCharactersWritten = Math.min(length, availableSpaceInBuffer);
        if (countCharactersWritten == 0) {
            return 0;
        }
	//copy the data into the write buffer
        writeCopy(this.senderBuffer, this.senderNextSeqNum, buf, pos, countCharactersWritten);
        int sequence_num = this.senderNextSeqNum;
        this.senderNextSeqNum += countCharactersWritten;
	// send as much data can be sent
        while (sequence_num < this.senderBase + this.senderWindow && sequence_num < this.senderNextSeqNum) {
            sequence_num += this.sendData(sequence_num);
        }
        return countCharactersWritten;
    }

     public static void writeCopy(final byte[] dest, int destPos, final byte[] src,  int srcPos, final int len) {
        destPos %= dest.length;
        if (destPos + len > dest.length) {
            int cnt = dest.length - destPos;
            System.arraycopy(src, srcPos, dest, destPos, cnt);
            srcPos += cnt;
            System.arraycopy(src, srcPos, dest, 0, len - cnt);
        }
        else {
            System.arraycopy(src, srcPos, dest, destPos, len);
        }
    }

 private int sendData(final int seq) {
        final int unsent = this.senderNextSeqNum - seq;
        final int inWnd = this.senderBase + this.senderWindow - seq;
        int cnt = Math.min(unsent, inWnd);
        cnt = Math.min(cnt, this.senderMSS);
        this.tcpManage.send(this.id, 3, 0, seq, this.senderBuffer, cnt);
        final int top = seq + cnt;
        if (this.senderUpperLimit < top) {
            this.senderUpperLimit = top;
        }
         if (seq == this.senderBase) {
            this.startRoundTripTimer(seq);
        }
        if (this.RTT_sample_seq < this.senderBase) {
            this.RTT_sample_seq = seq;
            this.RTT_sample_send_time = this.tcpManage.currentTime();
        }
        return cnt;
    }


    /**
     * Read from the socket up to len bytes into the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer
     * @param pos int starting position in buffer
     * @param len int number of bytes to read
     * @return int on success, the number of bytes read, which may be smaller
     *             than len; on failure, -1
     */
    public int read(byte[] buf, int pos, int len) {
	if (this.state == State.CLOSED) {
            return -1;
        }
        if (this.state != State.ESTABLISHED && this.state != State.SHUTDOWN) {
            return -1;
        }
        if (this.receiverBuffer == null) {
            return -1;
        }
        int available = Math.abs(this.receiverBase - this.receiverNextSeqNum);
        int cnt = Math.min(available, len);
        if (cnt == 0) {
            return 0;
        }
	//copy the data into into the buf from the receivebuffer
        readCopy(this.receiverBuffer, this.receiverNextSeqNum, buf, pos, cnt);
        this.receiverUpperLimit += cnt;
        this.receiverNextSeqNum += cnt;
        if (this.state == State.SHUTDOWN && this.receiverNextSeqNum == this.receiverBase) {
            this.state = State.CLOSED;
        }
        return cnt;
       // return -1;
    }

	public static void readCopy(final byte[] src, int srcPos, final byte[] dest, int destPos, final int len) {
        srcPos %= src.length;
        if (srcPos + len > src.length) {
            final int cnt = src.length - srcPos;
            System.arraycopy(src, srcPos, dest, destPos, cnt);
            destPos += cnt;
            System.arraycopy(src, 0, dest, destPos, len - cnt);
        }
        else {
            System.arraycopy(src, srcPos, dest, destPos, len);
        }
    }
	
	private void receiveDATApkt(final int srcAddress, final int destAddress, final Transport data) {
        final int seqNum = data.getSeqNum();
        if (seqNum == this.receiverBase) {
		//in order packet received
            System.out.print(".");
            final byte[] payload = data.getPayload();
            final int len = payload.length;
            final int avail = this.receiverUpperLimit - this.receiverBase;
            final int cnt = Math.min(avail, len);
	//put the data into receive buffer for the application to read
            writeCopy(this.receiverBuffer, this.receiverBase, payload, 0, cnt);
            this.receiverBase += cnt;
        }
        else {
		//out of order packet received
            System.out.print("&");
        }
        int receiverWindow = this.receiverUpperLimit - this.receiverBase;
        this.tcpManage.send(this.id, 1, receiverWindow, this.receiverBase, null, 0);
    }

    /*
     * End of socket API
     */

    public void OnReceive(final int srcAddress, final int destAddress, final Transport segment) {
        switch (segment.getType()) {
            case 0: {
                this.receiveSYNpkt(srcAddress, destAddress, segment);
                break;
            }
            case 1: {
                this.receiveACKpkt(srcAddress, destAddress, segment);
                break;
            }
            case 2: {
                this.receiveFINpkt(srcAddress, destAddress, segment);
                break;
            }
            case 3: {
                this.receiveDATApkt(srcAddress, destAddress, segment);
                break;
            }
        }
    }


 private void receiveSYNpkt(final int srcAddress, final int destAddress, final Transport syn) {
        System.out.print("S");
        if (this.state == State.LISTEN) {
            TCPSock conn = null;
            final TCPConnectionSocketIdentity sid = new TCPConnectionSocketIdentity();
            sid.setLocalAddress(this.id.getLocalAddress());
            sid.setLocalPort(this.id.getLocalPort());
            sid.setRemoteAddress(srcAddress);
            sid.setRemotePort(syn.getSrcPort());
            final int ackNum = syn.getSeqNum() + 1;
		//check if there is space in the queue
            if (this.pendingBacklogConnections.size() >= this.backlog || (conn = this.tcpManage.socket()) == null) {
                this.tcpManage.send(sid, 2, 0, ackNum, null, 0);
                return;
            }
            conn.setID(sid);
            conn.receiverBuffer = new byte[20000];
            int n = ackNum;
		//set up the socket for receiveing data
            conn.receiverNextSeqNum = n;
            conn.receiverBase = n;
            conn.receiverUpperLimit = conn.receiverBase + 19999;
            conn.receiverInitSeqNum = ackNum - 1;
	    int receiverWindow = conn.receiverUpperLimit - conn.receiverBase;
            this.tcpManage.send(sid, 1, receiverWindow, conn.receiverBase, null, 0);
            conn.setState(State.ESTABLISHED);
            this.pendingBacklogConnections.add(conn);
        }
        else if (this.state == State.ESTABLISHED && syn.getSeqNum() == this.receiverInitSeqNum) {
		//in case synack gets lost send another synack
            int receiverWindow = this.receiverUpperLimit - this.receiverBase;
            this.tcpManage.send(this.id, 1, receiverWindow, this.receiverBase, null, 0);
        }
    }

    private void setID(TCPConnectionSocketIdentity sid) {
	// TODO Auto-generated method stub
    	this.id = sid;
	
}

	private void setState(State established) {
	// TODO Auto-generated method stub
    	this.state = established;
	
}

	private void receiveFINpkt(final int srcAddr, final int destAddr, final Transport fin) {
        System.out.print("F");
        if (this.state == State.ESTABLISHED) {
            if (this.receiverNextSeqNum == this.receiverBase) {
                this.state = State.CLOSED;
            }
            else {
                this.state = State.SHUTDOWN;
            }
        }
    }

    
   private void receiveACKpkt(final int srcAddr, final int destAddr, final Transport ack) {
        final int ackNum = ack.getSeqNum();
        if (this.state == State.SYN_SENT && ackNum == this.senderBase + 1) {
		//ack received after SYN
            System.out.print(":");
            this.state = State.ESTABLISHED;
            this.senderBase = ackNum;
            this.senderNextSeqNum = ackNum;
/**/		this.senderReceiveWindow = ack.getWindow();
			this.updateSenderWindow();
            return;
        }
        if (this.state != State.ESTABLISHED && this.state != State.SHUTDOWN) {
            System.out.print("?");
            return;
        }
        if (ackNum > this.senderUpperLimit) {
            System.out.print("?");
            return;
        }
        /**/

        this.senderReceiveWindow = ack.getWindow();
        this.updateSenderWindow();
        
        if (ackNum > this.senderBase) {
		//ACK  received after data packet was received succesfully
            System.out.print(":");
            this.duplicateACK = 0;
            this.fastRetransmit = 0;
            this.retransmitWaiting = 0;
            this.retransmitExpired = 0;
            
            if (this.RTT_sample_seq >= this.senderBase && ackNum > this.RTT_sample_seq) {
                final long RTT_sample = this.tcpManage.currentTime() - this.RTT_sample_send_time;
                this.RTT_estimate = (long)(RTT_sample * 0.125) + (RTT_estimate*(long)0.875);
                this.RTTDev_estimate = (long) ((long)(Math.abs(this.RTT_estimate - RTT_sample) * 0.25) + ((long)RTTDev_estimate *0.75));
                this.RTO = this.RTT_estimate + 4L * this.RTTDev_estimate;
                /*timeout*/
            }
            final int nextToSend = Math.min(this.senderBase + this.senderWindow, this.senderUpperLimit);
           /**/
            if (this.senderCwnd < this.senderThreshold) {
//		slow start
                final int acked = (ackNum - this.senderBase + this.senderMSS - 1) /this.senderMSS;
                this.senderCwnd += acked;
            }
            else {
//AIMD congestion control
                this.senderCwnd += 1.0f / this.senderCwnd;
            }
            this.senderBase = ackNum;
            if (this.senderBase < this.senderNextSeqNum) {
                if (this.senderBase < this.senderUpperLimit) {
                    this.startRoundTripTimer(this.senderBase);
                }
               	//send remaining data after ack has been received
		int seq = Math.max(nextToSend, this.senderBase);
		while(seq < this.senderBase + this.senderWindow && seq < this.senderNextSeqNum){
			seq += this.sendData(seq);
		}
		

            }
            else if (this.state == State.SHUTDOWN) {
                this.tcpManage.send(this.id, 2, 0, this.senderBase, this.senderBuffer, 0);

                this.state = State.CLOSED;
            }
            return;
        }
	//duplicate ack received
        System.out.print("?");
        ++this.duplicateACK;
        if (this.duplicateACK == 3) {
            this.RTT_sample_seq = this.senderBase - 1;
            /**/
		//packet assumed lost, fastretransmit to start
            final float max = Math.max(this.senderCwnd / 2.0f, 1.0f);
            this.senderThreshold = max;
            this.senderCwnd = max;
            this.updateSenderWindow();
            if (this.fastRetransmit < 1) {
              
		int seq = this.senderBase;
		while(seq < this.senderBase + this.senderWindow && seq < this.senderNextSeqNum){
			seq += this.sendData(seq);
		}
                this.fastRetransmit += 1;
            }
        }
    }
   

   private void startRoundTripTimer(final int seq) {
       try {
           final String[] paramTypes = { "java.lang.Integer" };
           final Object[] params = { seq };
           final Method method = Callback.getMethod("retransmit", (Object)this,paramTypes);
           final Callback cb = new Callback(method, (Object)this, params);
           this.tcpManage.addTimer(this.RTO, cb);
           ++this.retransmitWaiting;
       }
       catch (Exception e) {
           e.printStackTrace();
           System.exit(1);
       }
   }

   public void retransmit(final Integer seqNum) {
       int seq = seqNum;
       if (this.senderBase > seq) {
           return;
       }
       if (this.retransmitWaiting-- > 1) {
           return;
       }
       this.retransmitExpired+=1;
       this.duplicateACK = 0;
       this.fastRetransmit = 0;
       this.RTT_sample_seq = this.senderBase - 1;
       /**/
       this.senderThreshold = Math.max(this.senderCwnd / 2.0f, 1.0f);
       this.senderCwnd = 1.0f;
       this.updateSenderWindow();
       if (this.retransmitExpired == 1) {
           this.RTO = RTO * 2L;
           /*timeout*/
           this.retransmitExpired = 0;
       }
       System.out.print("!");
       while (seq < this.senderBase + this.senderWindow && seq < this.senderNextSeqNum) {
           seq += this.sendData(seq);
       }
   }
   
   private void updateSenderWindow() {
       final int rwnd = Math.max(this.senderReceiveWindow, 1);
       final int cwnd = Math.round(this.senderCwnd) * this.senderMSS;
	
       this.senderWindow = Math.min(rwnd, cwnd);

	//String text = "Hello world";
        /*try {
          File file = new File("rwnd.txt");
          BufferedWriter output = new BufferedWriter(new FileWriter(file, true));
          output.write(rwnd + ", ");
          output.close();
        } catch ( IOException e ) {
           e.printStackTrace();
        }
	  try {
          File file = new File("cwnd.txt");
          BufferedWriter output = new BufferedWriter(new FileWriter(file, true));
          output.write(cwnd + ", ");
          output.close();
        } catch ( IOException e ) {
           e.printStackTrace();
        }
	  try {
          File file = new File("senderwnd.txt");
          BufferedWriter output = new BufferedWriter(new FileWriter(file, true));
          output.write(senderWindow + ", ");
          output.close();
        } catch ( IOException e ) {
           e.printStackTrace();
        }*/
   }


}
