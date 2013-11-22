public class TCPConnSockID
{
    public static final int INIT_PORT = 256;
    public static final int INIT_ADDRESS = 256;
    private int localAddress;
    private int localPort;
    private int remoteAddress;
    private int remotePort;

    public TCPConnSockID() {
        
        this.localAddress = -1;
        this.localPort = -1;
        this.remoteAddress = -1;
        this.remotePort = -1;
    }

    public int getLocalAddress() {
        return this.localAddress;
    }

    public void setLocalAddress(final int localAddress) {
        this.localAddress = localAddress;
    }

    public int getLocalPort() {
        return this.localPort;
    }

    public void setLocalPort(final int localPort) {
        this.localPort = localPort;
    }

    public int getRemoteAddress() {
        return this.remoteAddress;
    }

    public void setRemoteAddress(final int remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public int getRemotePort() {
        return this.remotePort;
    }

    public void setRemotePort(final int remotePort) {
        this.remotePort = remotePort;
    }
}
