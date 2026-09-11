package org.example.lab5;

record ReceivedDatagram(IpPacket ip, IcmpMessage icmp) {
    static ReceivedDatagram parse(byte[] raw, int length) {
        IpPacket ip = IpPacket.parse(raw, length);
        IcmpMessage icmp = IcmpMessage.parse(ip.payload());
        return new ReceivedDatagram(ip, icmp);
    }

    boolean belongsTo(int identifier) {
        return icmp.identifier() == identifier;
    }

    boolean isIcmpResponse() {
        return ip.protocol() == 1
                && (icmp.isEchoReply() || icmp.isTimeExceeded() || icmp.isDestinationUnreachable());
    }
}
