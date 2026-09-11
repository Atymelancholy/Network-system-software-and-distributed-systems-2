package org.example.lab5;

import com.sun.jna.Structure;

@Structure.FieldOrder({"sin_family", "sin_port", "sin_addr", "sin_zero"})
public class SockaddrIn extends Structure {
    public short sin_family;
    public byte[] sin_port = new byte[2];
    public byte[] sin_addr = new byte[4];
    public byte[] sin_zero = new byte[8];

    public SockaddrIn() {
        super();
    }

    void setFamily(int family) {
        this.sin_family = (short) family;
    }

    void setAddress(byte[] ipv4) {
        System.arraycopy(ipv4, 0, sin_addr, 0, 4);
    }

    byte[] addressBytes() {
        return sin_addr.clone();
    }
}
