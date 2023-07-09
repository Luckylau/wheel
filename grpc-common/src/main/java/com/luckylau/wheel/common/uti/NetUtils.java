package com.luckylau.wheel.common.uti;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class NetUtils {
    private static final String CLIENT_NAMING_LOCAL_IP_PROPERTY = "com.alibaba.nacos.client.naming.local.ip";

    private static final String LEGAL_LOCAL_IP_PROPERTY = "java.net.preferIPv6Addresses";

    private static final String DEFAULT_SOLVE_FAILED_RETURN = "resolve_failed";

    private static String localIp;

    /**
     * Get local ip.
     *
     * @return local ip
     */
    public static String localIP() {
        if (!StringUtils.isEmpty(localIp)) {
            return localIp;
        }

        String ip = System.getProperty(CLIENT_NAMING_LOCAL_IP_PROPERTY, findFirstNonLoopbackAddress());

        return localIp = ip;

    }

    private static String findFirstNonLoopbackAddress() {
        InetAddress result = null;

        try {
            int lowest = Integer.MAX_VALUE;
            for (Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
                 nics.hasMoreElements(); ) {
                NetworkInterface ifc = nics.nextElement();
                if (ifc.isUp()) {
                    if (ifc.getIndex() < lowest || result == null) {
                        lowest = ifc.getIndex();
                    } else {
                        continue;
                    }

                    for (Enumeration<InetAddress> addrs = ifc.getInetAddresses(); addrs.hasMoreElements(); ) {
                        InetAddress address = addrs.nextElement();
                        boolean isLegalIpVersion =
                                Boolean.parseBoolean(System.getProperty(LEGAL_LOCAL_IP_PROPERTY))
                                        ? address instanceof Inet6Address : address instanceof Inet4Address;
                        if (isLegalIpVersion && !address.isLoopbackAddress()) {
                            result = address;
                        }
                    }

                }
            }
        } catch (IOException ex) {
            //ignore
        }

        if (result != null) {
            return result.getHostAddress();
        }

        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            //ignore
        }

        return DEFAULT_SOLVE_FAILED_RETURN;

    }
}
