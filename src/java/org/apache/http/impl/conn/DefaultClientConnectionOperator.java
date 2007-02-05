/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.conn;

import java.io.IOException;
import java.net.Socket;
import java.net.InetAddress;

import org.apache.http.HttpHost;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HttpContext;

import org.apache.http.conn.Scheme;
import org.apache.http.conn.SchemeSet;
import org.apache.http.conn.SocketFactory;
import org.apache.http.conn.SecureSocketFactory;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.ClientConnectionOperator;


/**
 * Default implementation of a
 * {@link ClientConnectionOperator ClientConnectionOperator}.
 * It uses a {@link SchemeSet SchemeSet} to look up
 * {@link SocketFactory SocketFactory} objects.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$ $Date$
 *
 * @since 4.0
 */
public class DefaultClientConnectionOperator
    implements ClientConnectionOperator {


    /** The scheme set for looking up socket factories. */
    protected SchemeSet schemeSet;


    /**
     * Creates a new client connection operator.
     * Uses {@link SchemeSet#DEFAULT SchemeSet.DEFAULT}
     * as the scheme set.
     */
    public DefaultClientConnectionOperator() {
        this(null);
    }

    /**
     * Creates a new client connection operator for the given scheme set.
     *
     * @param schemes   the scheme set, or <code>null</code> to use
     *                  {@link SchemeSet#DEFAULT SchemeSet.DEFAULT}
     */
    public DefaultClientConnectionOperator(SchemeSet schemes) {
        schemeSet = (schemes != null) ? schemes : SchemeSet.DEFAULT;
    }



    // non-javadoc, see interface ClientConnectionOperator
    public void openConnection(OperatedClientConnection conn,
                               HttpHost target,
                               HttpContext context,
                               HttpParams params)
        throws IOException {

        if (conn == null) {
            throw new IllegalArgumentException
                ("Connection must not be null.");
        }
        if (target == null) {
            throw new IllegalArgumentException
                ("Target host must not be null.");
        }
        //@@@ is context allowed to be null?
        if (params == null) {
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        }
        if (conn.isOpen()) {
            throw new IllegalArgumentException
                ("Connection must not be open.");
        }

        InetAddress local = null;
        //@@@ TODO: deal with local address stuff (from context?)

        final Scheme schm = schemeSet.getScheme(target.getSchemeName());
        if (schm == null) {
            throw new IllegalArgumentException
                ("Unknown scheme '" + target.getSchemeName() +
                 "' in target host.");
        }
        final SocketFactory sf = schm.getSocketFactory();

        Socket sock = sf.createSocket();
        conn.announce(sock);

        sock = sf.connectSocket
            (sock, target.getHostName(), target.getPort(), local, 0, params);
        prepareSocket(sock, context, params);

        final boolean secure = sf.isSecure(sock);

        conn.open(sock, target, secure, params);
        //@@@ error handling: unannounce at connection?
        //@@@ error handling: close the created socket?

    } // openConnection


    // non-javadoc, see interface ClientConnectionOperator
    public void updateSecureConnection(OperatedClientConnection conn,
                                       HttpHost target,
                                       HttpContext context,
                                       HttpParams params)
        throws IOException {


        if (conn == null) {
            throw new IllegalArgumentException
                ("Connection must not be null.");
        }
        if (target == null) {
            throw new IllegalArgumentException
                ("Target host must not be null.");
        }
        //@@@ is context allowed to be null?
        if (params == null) {
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        }
        if (!conn.isOpen()) {
            throw new IllegalArgumentException
                ("Connection must be open.");
        }

        final Scheme schm = schemeSet.getScheme(target.getSchemeName());
        if (schm == null) {
            throw new IllegalArgumentException
                ("Unknown scheme '" + target.getSchemeName() +
                 "' in target host.");
        }
        if (!(schm.getSocketFactory() instanceof SecureSocketFactory)) {
            throw new IllegalArgumentException
                ("Target scheme (" + schm.getName() +
                 ") must have secure socket factory.");
        }

        final SecureSocketFactory ssf =
            (SecureSocketFactory)schm.getSocketFactory();
        final Socket sock = ssf.createSocket
            (conn.getSocket(), target.getHostName(), target.getPort(), true);
        prepareSocket(sock, context, params);

        final boolean secure = ssf.isSecure(sock);

        conn.update(sock, target, secure, params);
        //@@@ error handling: close the layered socket in case of exception?

    } // updateSecureConnection


    /**
     * Performs standard initializations on a newly created socket.
     *
     * @param sock      the socket to prepare
     * @param context   the context for the connection
     * @param params    the parameters from which to prepare the socket
     *
     * @throws IOException      in case of an IO problem
     */
    protected void prepareSocket(Socket sock, HttpContext context,
                                 HttpParams params)
        throws IOException {

        // context currently not used, but derived classes may need it
        //@@@ is context allowed to be null?

        sock.setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(params));
        sock.setSoTimeout(HttpConnectionParams.getSoTimeout(params));

        int linger = HttpConnectionParams.getLinger(params);
        if (linger >= 0) {
            sock.setSoLinger(linger > 0, linger);
        }

    } // prepareSocket


} // interface ClientConnectionOperator
