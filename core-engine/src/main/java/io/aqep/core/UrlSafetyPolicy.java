package io.aqep.core;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

public final class UrlSafetyPolicy {
    public URI validatePublicUrl(String value) {
        final URI uri;
        try {
            uri = URI.create(value).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid target URL", exception);
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only public HTTP and HTTPS URLs are supported");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Target URL must contain a public hostname and no credentials");
        }
        validatePublicHost(uri.getHost());
        return uri;
    }

    public void validatePublicHost(String host) {
        if (host.equalsIgnoreCase("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw new IllegalArgumentException("Local and private network targets are not allowed");
        }
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Target hostname could not be resolved: " + host, exception);
        }
        if (Arrays.stream(addresses).anyMatch(address -> address.isAnyLocalAddress()
                || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()
                || isIpv6UniqueLocal(address.getAddress()))) {
            throw new IllegalArgumentException("Local and private network targets are not allowed");
        }
    }

    public boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private boolean isIpv6UniqueLocal(byte[] address) {
        return address.length == 16 && (address[0] & 0xfe) == 0xfc;
    }
}
