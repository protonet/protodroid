/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.danopia.protonet.transport;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.danopia.protonet.R;
import net.danopia.protonet.bean.ChannelBean;
import net.danopia.protonet.bean.HostBean;
import net.danopia.protonet.service.TerminalBridge;
import net.danopia.protonet.service.TerminalManager;
import net.danopia.protonet.util.HostDatabase;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

/**
 * @author Kenny Root
 *
 */
public class SSH extends AbsTransport {
	public SSH() {
		super();
	}

	/**
	 * @param bridge
	 * @param db
	 */
	public SSH(HostBean host, TerminalBridge bridge, TerminalManager manager) {
		super(host, bridge, manager);
	}

	private static final String TAG = "ConnectBot.SSH";
	private static final int DEFAULT_PORT = 22;

	static final Pattern hostmask;
	static {
		hostmask = Pattern.compile("^(.+)@([0-9a-z.-]+)(:(\\d+))?$", Pattern.CASE_INSENSITIVE);
	}

	private volatile boolean authenticated = false;
	private volatile boolean connected = false;
	private volatile boolean sessionOpen = false;

	private List<ChannelBean> channels = new LinkedList<ChannelBean>();

	/*private void authenticate() {
		bridge.outputLine(manager.res.getString(R.string.terminal_auth));

		try {
			bridge.outputLine(manager.res.getString(R.string.terminal_auth_ki));
			if(connection.authenticateWithKeyboardInteractive(host.getUsername(), this)) {
				finishConnection();
			} else {
				bridge.outputLine(manager.res.getString(R.string.terminal_auth_ki_fail));
			}
		} catch (IllegalStateException e) {
			Log.e(TAG, "Connection went away while we were trying to authenticate", e);
			return;
		} catch(Exception e) {
			Log.e(TAG, "Problem during handleAuthentication()", e);
		}
	}*/

	/*
	 * Internal method to request actual PTY terminal once we've finished
	 * authentication. If called before authenticated, it will just fail.
	 *
	private void finishConnection() {
		authenticated = true;

		for (ChannelBean portForward : channels) {
			try {
				enableChannel(portForward);
				bridge.outputLine(manager.res.getString(R.string.terminal_enable_channel, portForward.getDescription()));
			} catch (Exception e) {
				Log.e(TAG, "Error setting up port forward during connect", e);
			}
		}

		if (!host.getWantSession()) {
			bridge.outputLine(manager.res.getString(R.string.terminal_no_session));
			bridge.onConnected();
			return;
		}

		try {
			//session = connection.openSession();

			//session.requestPTY(getEmulation(), columns, rows, width, height, null);
			//session.startShell();

			sessionOpen = true;

			bridge.onConnected();
		} catch (IOException e1) {
			Log.e(TAG, "Problem while trying to create PTY in finishConnection()", e1);
		}

	}
	*/

	@Override
	public void connect() {
		//connection = new Connection(host.getHostname(), host.getPort());
		//connection.addConnectionMonitor(this);

		// TODO: Connect and auth
	}

	@Override
	public void close() {
		connected = false;

		/*if (connection != null) {
			connection.close();
			connection = null;
		}*/
	}

	private void onDisconnect() {
		close();
	}

	@Override
	public Map<String, String> getOptions() {
		Map<String, String> options = new HashMap<String, String>();

		return options;
	}

	@Override
	public void setOptions(Map<String, String> options) {
	}

	@Override
	public boolean isSessionOpen() {
		return sessionOpen;
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	public void connectionLost(Throwable reason) {
		onDisconnect();
	}

	@Override
	public boolean canChannels() {
		return true;
	}

	@Override
	public List<ChannelBean> getChannels() {
		return channels;
	}

	@Override
	public boolean addChannel(ChannelBean portForward) {
		return channels.add(portForward);
	}

	@Override
	public boolean removeChannel(ChannelBean portForward) {
		// Make sure we don't have a phantom forwarder.
		disableChannel(portForward);

		return channels.remove(portForward);
	}

	@Override
	public boolean enableChannel(ChannelBean portForward) {
		if (!channels.contains(portForward)) {
			Log.e(TAG, "Attempt to enable port forward not in list");
			return false;
		}

		if (!authenticated)
			return false;

		// TODO: Subscribe to channel (if needed?)
		/*
		if (HostDatabase.PORTFORWARD_LOCAL.equals(portForward.getType())) {
			LocalPortForwarder lpf = null;
			try {
				lpf = connection.createLocalPortForwarder(
						new InetSocketAddress(InetAddress.getLocalHost(), portForward.getSourcePort()),
						portForward.getDestAddr(), portForward.getDestPort());
			} catch (Exception e) {
				Log.e(TAG, "Could not create local port forward", e);
				return false;
			}

			if (lpf == null) {
				Log.e(TAG, "returned LocalPortForwarder object is null");
				return false;
			}

			portForward.setIdentifier(lpf);
			portForward.setEnabled(true);
			return true;
		} else if (HostDatabase.PORTFORWARD_REMOTE.equals(portForward.getType())) {
			try {
				connection.requestRemotePortForwarding("", portForward.getSourcePort(), portForward.getDestAddr(), portForward.getDestPort());
			} catch (Exception e) {
				Log.e(TAG, "Could not create remote port forward", e);
				return false;
			}

			portForward.setEnabled(true);
			return true;
		} else if (HostDatabase.PORTFORWARD_DYNAMIC5.equals(portForward.getType())) {
			DynamicPortForwarder dpf = null;

			try {
				dpf = connection.createDynamicPortForwarder(
						new InetSocketAddress(InetAddress.getLocalHost(), portForward.getSourcePort()));
			} catch (Exception e) {
				Log.e(TAG, "Could not create dynamic port forward", e);
				return false;
			}

			portForward.setIdentifier(dpf);
			portForward.setEnabled(true);
			return true;
		} else {
			// Unsupported type
			Log.e(TAG, String.format("attempt to forward unknown type %s", portForward.getType()));
			return false;
		}
		*/
		return true;
	}

	@Override
	public boolean disableChannel(ChannelBean portForward) {
		if (!channels.contains(portForward)) {
			Log.e(TAG, "Attempt to disable port forward not in list");
			return false;
		}

		if (!authenticated)
			return false;

		// TODO: Unsubscribe to channel (if needed?)
		/*
		if (HostDatabase.PORTFORWARD_LOCAL.equals(portForward.getType())) {
			LocalPortForwarder lpf = null;
			lpf = (LocalPortForwarder)portForward.getIdentifier();

			if (!portForward.isEnabled() || lpf == null) {
				Log.d(TAG, String.format("Could not disable %s; it appears to be not enabled or have no handler", portForward.getNickname()));
				return false;
			}

			portForward.setEnabled(false);

			try {
				lpf.close();
			} catch (IOException e) {
				Log.e(TAG, "Could not stop local port forwarder, setting enabled to false", e);
				return false;
			}

			return true;
		} else if (HostDatabase.PORTFORWARD_REMOTE.equals(portForward.getType())) {
			portForward.setEnabled(false);

			try {
				connection.cancelRemotePortForwarding(portForward.getSourcePort());
			} catch (IOException e) {
				Log.e(TAG, "Could not stop remote port forwarding, setting enabled to false", e);
				return false;
			}

			return true;
		} else if (HostDatabase.PORTFORWARD_DYNAMIC5.equals(portForward.getType())) {
			DynamicPortForwarder dpf = null;
			dpf = (DynamicPortForwarder)portForward.getIdentifier();

			if (!portForward.isEnabled() || dpf == null) {
				Log.d(TAG, String.format("Could not disable %s; it appears to be not enabled or have no handler", portForward.getNickname()));
				return false;
			}

			portForward.setEnabled(false);

			try {
				dpf.close();
			} catch (IOException e) {
				Log.e(TAG, "Could not stop dynamic port forwarder, setting enabled to false", e);
				return false;
			}

			return true;
		} else {
			// Unsupported type
			Log.e(TAG, String.format("attempt to forward unknown type %s", portForward.getType()));
			return false;
		}
		*/
		return true;
	}

	@Override
	public int getDefaultPort() {
		return DEFAULT_PORT;
	}

	@Override
	public String getDefaultNickname(String username, String hostname, int port) {
		if (port == DEFAULT_PORT) {
			return String.format("%s@%s", username, hostname);
		} else {
			return String.format("%s@%s:%d", username, hostname, port);
		}
	}

	public static Uri getUri(String input) {
		Matcher matcher = hostmask.matcher(input);

		if (!matcher.matches())
			return null;

		StringBuilder sb = new StringBuilder("ptn://");

		sb.append(Uri.encode(matcher.group(1)))
			.append('@')
			.append(matcher.group(2));

		String portString = matcher.group(4);
		int port = DEFAULT_PORT;
		if (portString != null) {
			try {
				port = Integer.parseInt(portString);
				if (port < 1 || port > 65535) {
					port = DEFAULT_PORT;
				}
			} catch (NumberFormatException nfe) {
				// Keep the default port
			}
		}

		if (port != DEFAULT_PORT) {
			sb.append(':')
				.append(port);
		}

		sb.append("/#")
			.append(Uri.encode(input));

		Uri uri = Uri.parse(sb.toString());

		return uri;
	}

	@Override
	public HostBean createHost(Uri uri) {
		HostBean host = new HostBean();

		host.setHostname(uri.getHost());

		int port = uri.getPort();
		if (port < 0)
			port = DEFAULT_PORT;
		host.setPort(port);

		host.setUsername(uri.getUserInfo());

		String nickname = uri.getFragment();
		if (nickname == null || nickname.length() == 0) {
			host.setNickname(getDefaultNickname(host.getUsername(),
					host.getHostname(), host.getPort()));
		} else {
			host.setNickname(uri.getFragment());
		}

		return host;
	}

	@Override
	public void getSelectionArgs(Uri uri, Map<String, String> selection) {
		selection.put(HostDatabase.FIELD_HOST_NICKNAME, uri.getFragment());
		selection.put(HostDatabase.FIELD_HOST_HOSTNAME, uri.getHost());

		int port = uri.getPort();
		if (port < 0)
			port = DEFAULT_PORT;
		selection.put(HostDatabase.FIELD_HOST_PORT, Integer.toString(port));
		selection.put(HostDatabase.FIELD_HOST_USERNAME, uri.getUserInfo());
	}

	public static String getFormatHint(Context context) {
		return String.format("%s@%s:%s",
				context.getString(R.string.format_username),
				context.getString(R.string.format_hostname),
				context.getString(R.string.format_port));
	}

	/* (non-Javadoc)
	 * @see org.connectbot.transport.AbsTransport#usesNetwork()
	 */
	@Override
	public boolean usesNetwork() {
		return true;
	}

	/* (non-Javadoc)
	 * @see net.danopia.protonet.transport.AbsTransport#flush()
	 */
	@Override
	public void flush() throws IOException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see net.danopia.protonet.transport.AbsTransport#read(byte[], int, int)
	 */
	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see net.danopia.protonet.transport.AbsTransport#setDimensions(int, int, int, int)
	 */
	@Override
	public void setDimensions(int columns, int rows, int width, int height) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see net.danopia.protonet.transport.AbsTransport#write(byte[])
	 */
	@Override
	public void write(byte[] buffer) throws IOException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see net.danopia.protonet.transport.AbsTransport#write(int)
	 */
	@Override
	public void write(int c) throws IOException {
		// TODO Auto-generated method stub

	}
}
