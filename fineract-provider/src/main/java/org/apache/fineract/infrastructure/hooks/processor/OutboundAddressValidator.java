/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.hooks.processor;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether an outbound (webhook) request may be sent to an address. Only http and https are allowed and, unless
 * private addresses are explicitly permitted, the host must not resolve to a loopback, any-local, link-local,
 * site-local/private, carrier-grade NAT, unique-local or multicast address.
 */
public final class OutboundAddressValidator {

    private OutboundAddressValidator() {}

    /**
     * @throws IllegalArgumentException
     *             if the URL is not an http(s) URL, the host cannot be resolved or (when private addresses are not
     *             allowed) any address of the host is not publicly routable
     */
    public static void validateUrl(final String url, final boolean allowPrivateAddresses) {
        final URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL", e);
        }
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http and https URLs are allowed");
        }
        final String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("URL has no host");
        }
        if (allowPrivateAddresses) {
            return;
        }
        try {
            checkAddresses(Arrays.asList(InetAddress.getAllByName(host)), host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Host cannot be resolved", e);
        }
    }

    /**
     * @return the given addresses if all of them are allowed
     * @throws UnknownHostException
     *             if any of the addresses is not allowed
     */
    public static List<InetAddress> filterResolved(final String host, final List<InetAddress> addresses) throws UnknownHostException {
        try {
            checkAddresses(addresses, host);
        } catch (IllegalArgumentException e) {
            throw new UnknownHostException(e.getMessage());
        }
        return new ArrayList<>(addresses);
    }

    private static void checkAddresses(final List<InetAddress> addresses, final String host) {
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be resolved");
        }
        for (final InetAddress address : addresses) {
            if (isRestricted(address)) {
                throw new IllegalArgumentException("Host " + host + " resolves to a non-public address");
            }
        }
    }

    public static boolean isRestricted(final InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        final byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            final int first = b[0] & 0xff;
            final int second = b[1] & 0xff;
            // 0.0.0.0/8, 100.64.0.0/10 (carrier-grade NAT), 255.255.255.255
            return first == 0 || (first == 100 && (second & 0xc0) == 64)
                    || (first == 255 && second == 255 && (b[2] & 0xff) == 255 && (b[3] & 0xff) == 255);
        }
        if (address instanceof Inet6Address) {
            // fc00::/7 (unique local), fe80::/10 (link-local) and fec0::/10 (deprecated site-local)
            final int first = b[0] & 0xff;
            return (first & 0xfe) == 0xfc || (first == 0xfe && (b[1] & 0xc0) >= 0x80);
        }
        return false;
    }
}
