package com.fibbage.network;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * NetworkUtils detects local network IP addresses so players on other devices
 * (smartphones, tablets, laptops) can join the game session across the LAN / Wi-Fi.
 */
public class NetworkUtils {

    /**
     * Finds the most likely active LAN IPv4 address (e.g. 192.168.1.X or 10.0.0.X).
     */
    public static String getLocalIpAddress() {
        // Strategy 1: UDP socket trick (does not send packets, but asks OS routing table)
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            InetAddress addr = socket.getLocalAddress();
            if (addr != null && !addr.isLoopbackAddress() && addr.getHostAddress().contains(".")
                    && isPrivateLan(addr.getHostAddress())) {
                return addr.getHostAddress();
            }
        } catch (Exception ignored) {}

        // Strategy 2: Scan all active network interfaces
        List<String> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                String name = ni.getName().toLowerCase();
                String display = ni.getDisplayName().toLowerCase();
                // Skip common virtual adapters (VMware, VirtualBox, vEthernet, Docker)
                if (display.contains("virtual") || display.contains("vmware") || display.contains("vbox") || display.contains("docker")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().contains(".")) {
                        String ip = addr.getHostAddress();
                        if (isPrivateLan(ip)) {
                            return ip; // Top preference: standard home/office LAN
                        }
                        candidates.add(ip);
                    }
                }
            }
        } catch (Exception ignored) {}

        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }

        // Strategy 3: Standard getLocalHost
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            if (localHost != null && !localHost.isLoopbackAddress()) {
                return localHost.getHostAddress();
            }
        } catch (Exception ignored) {}

        return "localhost";
    }

    /** True for the private ranges a home / office / hotspot Wi-Fi uses (phones can reach these). */
    private static boolean isPrivateLan(String ip) {
        if (ip.startsWith("192.168.") || ip.startsWith("10.")) return true;
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Builds the join URL for a given room.
     */
    public static String getJoinUrl(String host, int webPort, String roomCode) {
        String base = "http://" + host + ":" + webPort;
        if (roomCode != null && !roomCode.trim().isEmpty()) {
            return base + "/?room=" + roomCode.trim().toUpperCase();
        }
        return base;
    }
}
