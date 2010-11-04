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

package net.danopia.protonet.service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.danopia.protonet.R;
import net.danopia.protonet.TerminalView;
import net.danopia.protonet.bean.ChannelBean;
import net.danopia.protonet.bean.HostBean;
import android.util.Log;


/**
 * Provides a bridge between a MUD terminal buffer and a possible TerminalView.
 * This separation allows us to keep the TerminalBridge running in a background
 * service. A TerminalView shares down a bitmap that we can use for rendering
 * when available.
 *
 * This class also provides SSH hostkey verification prompting, and password
 * prompting.
 */
public class TerminalBridge {
	public final static String TAG = "ConnectBot.TerminalBridge";

	public Integer[] color;

	protected final TerminalManager manager;

	public HostBean host;

	/* package */ Transport transport;

	private Relay relay;

	private final int scrollback;

	public ArrayList<String> buffer = null;

	private TerminalView parent = null;

	private boolean disconnected = false;
	private boolean awaitingClose = false;

	private final List<String> localOutput;

	public PromptHelper promptHelper;

	protected BridgeDisconnectedListener disconnectListener = null;

	/**
	 * Create a new terminal bridge suitable for unit testing.
	 */
	public TerminalBridge() {
		buffer = new ArrayList<String>();

		manager = null;

		scrollback = 1;

		localOutput = new LinkedList<String>();

		transport = null;
	}

	/**
	 * Create new terminal bridge with following parameters. We will immediately
	 * launch thread to start SSH connection and handle any hostkey verification
	 * and password authentication.
	 */
	public TerminalBridge(final TerminalManager manager, final HostBean host) throws IOException {
		this.manager = manager;
		this.host = host;

		scrollback = manager.getScrollback();

		// create prompt helper to relay password and hostkey requests up to gui
		promptHelper = new PromptHelper(this);

		localOutput = new LinkedList<String>();

		// create terminal buffer and handle outgoing data
		// this is probably status reply information
		buffer = new ArrayList<String>();

		//buffer.setBufferSize(scrollback);
	}

	public PromptHelper getPromptHelper() {
		return promptHelper;
	}

	/**
	 * Spawn thread to open connection and start login process.
	 */
	protected void startConnection() {
		transport = new Transport();
		transport.setBridge(this);
		transport.setManager(manager);
		transport.setHost(host);

		if (transport.canChannels()) {
			for (ChannelBean portForward : manager.hostdb.getChannelsForHost(host))
				transport.addChannel(portForward);
		}

		outputLine(manager.res.getString(R.string.terminal_connecting, host.getHostname(), host.getPort()));

		Thread connectionThread = new Thread(new Runnable() {
			public void run() {
				transport.connect();
			}
		});
		connectionThread.setName("Connection");
		connectionThread.setDaemon(true);
		connectionThread.start();
	}

	/**
	 * Handle challenges from keyboard-interactive authentication mode.
	 */
	public String[] replyToChallenge(String name, String instruction, int numPrompts, String[] prompt, boolean[] echo) {
		String[] responses = new String[numPrompts];
		for(int i = 0; i < numPrompts; i++) {
			// request response from user for each prompt
			responses[i] = promptHelper.requestStringPrompt(instruction, prompt[i]);
		}
		return responses;
	}

	/**
	 * @return charset in use by bridge
	 */
	public Charset getCharset() {
		return relay.getCharset();
	}

	/**
	 * Sets the encoding used by the terminal. If the connection is live,
	 * then the character set is changed for the next read.
	 * @param encoding the canonical name of the character encoding
	 */
	public void setCharset(String encoding) {
		if (relay != null)
			relay.setCharset(encoding);
	}

	/**
	 * Convenience method for writing a line into the underlying MUD buffer.
	 * Should never be called once the session is established.
	 */
	public final void outputLine(String line) {
		if (transport != null && transport.isSessionOpen())
			Log.e(TAG, "Session established, cannot use outputLine!", new IOException("outputLine call traceback"));

		synchronized (localOutput) {
			final String s = line + "\r\n";

			localOutput.add(s);

			buffer.add(s);
		}
	}

	/**
	 * Inject a specific string into this terminal. Used for post-login strings
	 * and pasting clipboard.
	 */
	public void injectString(final String string) {
		if (string == null || string.length() == 0)
			return;

		Thread injectStringThread = new Thread(new Runnable() {
			public void run() {
				try {
					transport.write(string.getBytes(host.getEncoding()));
				} catch (Exception e) {
					Log.e(TAG, "Couldn't inject string to remote host: ", e);
				}
			}
		});
		injectStringThread.setName("InjectString");
		injectStringThread.start();
	}

	/**
	 * Internal method to request actual PTY terminal once we've finished
	 * authentication. If called before authenticated, it will just fail.
	 */
	public void onConnected() {
		disconnected = false;

		buffer.clear();

		// We no longer need our local output.
		localOutput.clear();

		// create thread to relay incoming connection data to buffer
		relay = new Relay(this, transport, buffer, host.getEncoding());
		Thread relayThread = new Thread(relay);
		relayThread.setDaemon(true);
		relayThread.setName("Relay");
		relayThread.start();
	}

	/**
	 * @return whether a session is open or not
	 */
	public boolean isSessionOpen() {
		if (transport != null)
			return transport.isSessionOpen();
		return false;
	}

	public void setOnDisconnectedListener(BridgeDisconnectedListener disconnectListener) {
		this.disconnectListener = disconnectListener;
	}

	/**
	 * Force disconnection of this terminal bridge.
	 */
	public void dispatchDisconnect(boolean immediate) {
		// We don't need to do this multiple times.
		synchronized (this) {
			if (disconnected && !immediate)
				return;

			disconnected = true;
		}

		// Cancel any pending prompts.
		promptHelper.cancelPrompt();

		// disconnection request hangs if we havent really connected to a host yet
		// temporary fix is to just spawn disconnection into a thread
		Thread disconnectThread = new Thread(new Runnable() {
			public void run() {
				if (transport != null && transport.isConnected())
					transport.close();
			}
		});
		disconnectThread.setName("Disconnect");
		disconnectThread.start();

		if (immediate) {
			awaitingClose = true;
			if (disconnectListener != null)
				disconnectListener.onDisconnected(TerminalBridge.this);
		} else {
			{
				final String line = manager.res.getString(R.string.alert_disconnect_msg);
				buffer.add("\r\n" + line + "\r\n");
			}
			if (host.getStayConnected()) {
				manager.requestReconnect(this);
				return;
			}
			Thread disconnectPromptThread = new Thread(new Runnable() {
				public void run() {
					Boolean result = promptHelper.requestBooleanPrompt(null,
							manager.res.getString(R.string.prompt_host_disconnected));
					if (result == null || result.booleanValue()) {
						awaitingClose = true;

						// Tell the TerminalManager that we can be destroyed now.
						if (disconnectListener != null)
							disconnectListener.onDisconnected(TerminalBridge.this);
					}
				}
			});
			disconnectPromptThread.setName("DisconnectPrompt");
			disconnectPromptThread.setDaemon(true);
			disconnectPromptThread.start();
		}
	}

	public synchronized void tryKeyVibrate() {
		manager.tryKeyVibrate();
	}

	/**
	 * Somehow our parent {@link TerminalView} was destroyed. Now we don't need
	 * to redraw anywhere, and we can recycle our internal bitmap.
	 */
	public synchronized void parentDestroyed() {
		parent = null;
	}

	public void redraw() {
		if (parent != null)
			parent.postInvalidate();
	}

	/**
	 * Adds the {@link ChannelBean} to the list.
	 * @param portForward the port forward bean to add
	 * @return true on successful addition
	 */
	public boolean addChannel(ChannelBean portForward) {
		return transport.addChannel(portForward);
	}

	/**
	 * Removes the {@link ChannelBean} from the list.
	 * @param portForward the port forward bean to remove
	 * @return true on successful removal
	 */
	public boolean removeChannel(ChannelBean portForward) {
		return transport.removeChannel(portForward);
	}

	/**
	 * @return the list of port forwards
	 */
	public List<ChannelBean> getChannels() {
		return transport.getChannels();
	}

	/**
	 * Enables a port forward member. After calling this method, the port forward should
	 * be operational.
	 * @param portForward member of our current port forwards list to enable
	 * @return true on successful port forward setup
	 */
	public boolean enableChannel(ChannelBean portForward) {
		if (!transport.isConnected()) {
			Log.i(TAG, "Attempt to enable port forward while not connected");
			return false;
		}

		return transport.enableChannel(portForward);
	}

	/**
	 * Disables a port forward member. After calling this method, the port forward should
	 * be non-functioning.
	 * @param portForward member of our current port forwards list to enable
	 * @return true on successful port forward tear-down
	 */
	public boolean disableChannel(ChannelBean portForward) {
		if (!transport.isConnected()) {
			Log.i(TAG, "Attempt to disable port forward while not connected");
			return false;
		}

		return transport.disableChannel(portForward);
	}

	/**
	 * @return whether the TerminalBridge should close
	 */
	public boolean isAwaitingClose() {
		return awaitingClose;
	}

	/**
	 * @return whether this connection had started and subsequently disconnected
	 */
	public boolean isDisconnected() {
		return disconnected;
	}
}
