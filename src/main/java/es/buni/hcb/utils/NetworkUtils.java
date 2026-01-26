package es.buni.hcb.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public final class NetworkUtils {

    private NetworkUtils() {}

    public static InetAddress getFirstUsableIPv4() throws Exception {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface nif = interfaces.nextElement();

            if (!nif.isUp() || nif.isLoopback() || nif.isVirtual() || nif.isPointToPoint()) {
                continue;
            }

            Enumeration<InetAddress> addresses = nif.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();

                if (addr instanceof Inet4Address &&
                        !addr.isLoopbackAddress() &&
                        !addr.isLinkLocalAddress()) {
                    return addr;
                }
            }
        }

        throw new IllegalStateException("No usable IPv4 address found");
    }
}
