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
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.danopia.protonet.R;
import net.danopia.protonet.TerminalView;
import net.danopia.protonet.bean.ChannelBean;
import net.danopia.protonet.bean.HostBean;
import net.danopia.protonet.bean.SelectionArea;
import net.danopia.protonet.transport.AbsTransport;
import net.danopia.protonet.transport.TransportFactory;
import net.danopia.protonet.util.HostDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;
import de.mud.terminal.VDUBuffer;
import de.mud.terminal.VDUDisplay;
import de.mud.terminal.vt320;


/**
 * Provides a bridge between a MUD terminal buffer and a possible TerminalView.
 * This separation allows us to keep the TerminalBridge running in a background
 * service. A TerminalView shares down a bitmap that we can use for rendering
 * when available.
 *
 * This class also provides SSH hostkey verification prompting, and password
 * prompting.
 */
public class TerminalBridge implements VDUDisplay {
	public final static String TAG = "ConnectBot.TerminalBridge";

	public Integer[] color;

	public int defaultFg = HostDatabase.DEFAULT_FG_COLOR;
	public int defaultBg = HostDatabase.DEFAULT_BG_COLOR;

	protected final TerminalManager manager;

	public HostBean host;

	/* package */ AbsTransport transport;

	final Paint defaultPaint;

	private Relay relay;

	private final int scrollback;

	public Bitmap bitmap = null;
	public VDUBuffer buffer = null;

	private TerminalView parent = null;
	private final Canvas canvas = new Canvas();

	private boolean disconnected = false;
	private boolean awaitingClose = false;

	/* package */ final TerminalKeyListener keyListener;

	private boolean selectingForCopy = false;
	private final SelectionArea selectionArea;

	public int charWidth = -1;
	public int charHeight = -1;
	private int charTop = -1;

	private final List<String> localOutput;

	/**
	 * Flag indicating if we should perform a full-screen redraw during our next
	 * rendering pass.
	 */
	private boolean fullRedraw = false;

	public PromptHelper promptHelper;

	protected BridgeDisconnectedListener disconnectListener = null;

	/**
	 * Create a new terminal bridge suitable for unit testing.
	 */
	public TerminalBridge() {
		buffer = new vt320() {
			@Override
			public void write(byte[] b) {}
			@Override
			public void write(int b) {}
			@Override
			public void sendTelnetCommand(byte cmd) {}
			@Override
			public void setWindowSize(int c, int r) {}
			@Override
			public void debug(String s) {}
		};

		manager = null;

		defaultPaint = new Paint();

		selectionArea = new SelectionArea();
		scrollback = 1;

		localOutput = new LinkedList<String>();

		transport = null;

		keyListener = new TerminalKeyListener(manager, this, buffer, null);
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

		// create our default paint
		defaultPaint = new Paint();
		defaultPaint.setAntiAlias(true);
		defaultPaint.setTypeface(Typeface.MONOSPACE);
		defaultPaint.setFakeBoldText(true); // more readable?

		localOutput = new LinkedList<String>();

		// create terminal buffer and handle outgoing data
		// this is probably status reply information
		buffer = new vt320() {
			@Override
			public void debug(String s) {
				Log.d(TAG, s);
			}

			@Override
			public void write(byte[] b) {
				try {
					if (b != null && transport != null)
						transport.write(b);
				} catch (IOException e) {
					Log.e(TAG, "Problem writing outgoing data in vt320() thread", e);
				}
			}

			@Override
			public void write(int b) {
				try {
					if (transport != null)
						transport.write(b);
				} catch (IOException e) {
					Log.e(TAG, "Problem writing outgoing data in vt320() thread", e);
				}
			}

			// We don't use telnet sequences.
			@Override
			public void sendTelnetCommand(byte cmd) {
			}

			// We don't want remote to resize our window.
			@Override
			public void setWindowSize(int c, int r) {
			}

			@Override
			public void beep() {
				if (parent.isShown())
					manager.playBeep();
				else
					manager.sendActivityNotification(host);
			}
		};

		// Don't keep any scrollback if a session is not being opened.
		if (host.getWantSession())
			buffer.setBufferSize(scrollback);
		else
			buffer.setBufferSize(0);

		resetColors();
		buffer.setDisplay(this);

		selectionArea = new SelectionArea();

		keyListener = new TerminalKeyListener(manager, this, buffer, host.getEncoding());
	}

	public PromptHelper getPromptHelper() {
		return promptHelper;
	}

	/**
	 * Spawn thread to open connection and start login process.
	 */
	protected void startConnection() {
		transport = TransportFactory.getTransport();
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
		keyListener.setCharset(encoding);
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

			((vt320) buffer).putString(s);
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

		((vt320) buffer).reset();

		// We no longer need our local output.
		localOutput.clear();

		// create thread to relay incoming connection data to buffer
		relay = new Relay(this, transport, (vt320) buffer, host.getEncoding());
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
				((vt320) buffer).putString("\r\n" + line + "\r\n");
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

	public void setSelectingForCopy(boolean selectingForCopy) {
		this.selectingForCopy = selectingForCopy;
	}

	public boolean isSelectingForCopy() {
		return selectingForCopy;
	}

	public SelectionArea getSelectionArea() {
		return selectionArea;
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
		discardBitmap();
	}

	private void discardBitmap() {
		if (bitmap != null)
			bitmap.recycle();
		bitmap = null;
	}

	public void setVDUBuffer(VDUBuffer buffer) {
		this.buffer = buffer;
	}

	public VDUBuffer getVDUBuffer() {
		return buffer;
	}

	public void onDraw() {
		int fg, bg;
		synchronized (buffer) {
			boolean entireDirty = buffer.update[0] || fullRedraw;
			boolean isWideCharacter = false;

			// walk through all lines in the buffer
			for(int l = 0; l < buffer.height; l++) {

				// check if this line is dirty and needs to be repainted
				// also check for entire-buffer dirty flags
				if (!entireDirty && !buffer.update[l + 1]) continue;

				// reset dirty flag for this line
				buffer.update[l + 1] = false;

				// walk through all characters in this line
				for (int c = 0; c < buffer.width; c++) {
					int addr = 0;
					int currAttr = buffer.charAttributes[buffer.windowBase + l][c];

					{
						int fgcolor = defaultFg;

						// check if foreground color attribute is set
						if ((currAttr & VDUBuffer.COLOR_FG) != 0)
							fgcolor = ((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1;

						if (fgcolor < 8 && (currAttr & VDUBuffer.BOLD) != 0)
							fg = color[fgcolor + 8];
						else
							fg = color[fgcolor];
					}

					// check if background color attribute is set
					if ((currAttr & VDUBuffer.COLOR_BG) != 0)
						bg = color[((currAttr & VDUBuffer.COLOR_BG) >> VDUBuffer.COLOR_BG_SHIFT) - 1];
					else
						bg = color[defaultBg];

					// support character inversion by swapping background and foreground color
					if ((currAttr & VDUBuffer.INVERT) != 0) {
						int swapc = bg;
						bg = fg;
						fg = swapc;
					}

					// set underlined attributes if requested
					defaultPaint.setUnderlineText((currAttr & VDUBuffer.UNDERLINE) != 0);

					isWideCharacter = (currAttr & VDUBuffer.FULLWIDTH) != 0;

					if (isWideCharacter)
						addr++;
					else {
						// determine the amount of continuous characters with the same settings and print them all at once
						while(c + addr < buffer.width
								&& buffer.charAttributes[buffer.windowBase + l][c + addr] == currAttr) {
							addr++;
						}
					}

					// Save the current clip region
					canvas.save(Canvas.CLIP_SAVE_FLAG);

					// clear this dirty area with background color
					defaultPaint.setColor(bg);
					if (isWideCharacter) {
						canvas.clipRect(c * charWidth,
								l * charHeight,
								(c + 2) * charWidth,
								(l + 1) * charHeight);
					} else {
						canvas.clipRect(c * charWidth,
								l * charHeight,
								(c + addr) * charWidth,
								(l + 1) * charHeight);
					}
					canvas.drawPaint(defaultPaint);

					// write the text string starting at 'c' for 'addr' number of characters
					defaultPaint.setColor(fg);
					if((currAttr & VDUBuffer.INVISIBLE) == 0)
						canvas.drawText(buffer.charArray[buffer.windowBase + l], c,
							addr, c * charWidth, (l * charHeight) - charTop,
							defaultPaint);

					// Restore the previous clip region
					canvas.restore();

					// advance to the next text block with different characteristics
					c += addr - 1;
					if (isWideCharacter)
						c++;
				}
			}

			// reset entire-buffer flags
			buffer.update[0] = false;
		}
		fullRedraw = false;
	}

	public void redraw() {
		if (parent != null)
			parent.postInvalidate();
	}

	// We don't have a scroll bar.
	public void updateScrollBar() {
	}

	/**
	 * @return whether underlying transport can forward ports
	 */
	public boolean canChannels() {
		return transport.canChannels();
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

	/* (non-Javadoc)
	 * @see de.mud.terminal.VDUDisplay#setColor(byte, byte, byte, byte)
	 */
	public void setColor(int index, int red, int green, int blue) {
		// Don't allow the system colors to be overwritten for now. May violate specs.
		if (index < color.length && index >= 16)
			color[index] = 0xff000000 | red << 16 | green << 8 | blue;
	}

	public final void resetColors() {
		int[] defaults = manager.hostdb.getDefaultColorsForScheme(HostDatabase.DEFAULT_COLOR_SCHEME);
		defaultFg = defaults[0];
		defaultBg = defaults[1];

		color = manager.hostdb.getColorsForScheme(HostDatabase.DEFAULT_COLOR_SCHEME);
	}

	private static Pattern urlPattern = null;

	/**
	 * @return
	 */
	public List<String> scanForURLs() {
		List<String> urls = new LinkedList<String>();

		if (urlPattern == null) {
			// based on http://www.ietf.org/rfc/rfc2396.txt
			String scheme = "[A-Za-z][-+.0-9A-Za-z]*";
			String unreserved = "[-._~0-9A-Za-z]";
			String pctEncoded = "%[0-9A-Fa-f]{2}";
			String subDelims = "[!$&'()*+,;:=]";
			String userinfo = "(?:" + unreserved + "|" + pctEncoded + "|" + subDelims + "|:)*";
			String h16 = "[0-9A-Fa-f]{1,4}";
			String decOctet = "(?:[0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])";
			String ipv4address = decOctet + "\\." + decOctet + "\\." + decOctet + "\\." + decOctet;
			String ls32 = "(?:" + h16 + ":" + h16 + "|" + ipv4address + ")";
			String ipv6address = "(?:(?:" + h16 + "){6}" + ls32 + ")";
			String ipvfuture = "v[0-9A-Fa-f]+.(?:" + unreserved + "|" + subDelims + "|:)+";
			String ipLiteral = "\\[(?:" + ipv6address + "|" + ipvfuture + ")\\]";
			String regName = "(?:" + unreserved + "|" + pctEncoded + "|" + subDelims + ")*";
			String host = "(?:" + ipLiteral + "|" + ipv4address + "|" + regName + ")";
			String port = "[0-9]*";
			String authority = "(?:" + userinfo + "@)?" + host + "(?::" + port + ")?";
			String pchar = "(?:" + unreserved + "|" + pctEncoded + "|" + subDelims + ")";
			String segment = pchar + "*";
			String pathAbempty = "(?:/" + segment + ")*";
			String segmentNz = pchar + "+";
			String pathAbsolute = "/(?:" + segmentNz + "(?:/" + segment + ")*)?";
			String pathRootless = segmentNz + "(?:/" + segment + ")*";
			String hierPart = "(?://" + authority + pathAbempty + "|" + pathAbsolute + "|" + pathRootless + ")";
			String query = "(?:" + pchar + "|/|\\?)*";
			String fragment = "(?:" + pchar + "|/|\\?)*";
			String uriRegex = scheme + ":" + hierPart + "(?:" + query + ")?(?:#" + fragment + ")?";
			urlPattern = Pattern.compile(uriRegex);
		}

		char[] visibleBuffer = new char[buffer.height * buffer.width];
		for (int l = 0; l < buffer.height; l++)
			System.arraycopy(buffer.charArray[buffer.windowBase + l], 0,
					visibleBuffer, l * buffer.width, buffer.width);

		Matcher urlMatcher = urlPattern.matcher(new String(visibleBuffer));
		while (urlMatcher.find())
			urls.add(urlMatcher.group());

		return urls;
	}

	/**
	 * @return
	 */
	public boolean isUsingNetwork() {
		return transport.usesNetwork();
	}

	/**
	 * @return
	 */
	public TerminalKeyListener getKeyHandler() {
		return keyListener;
	}

	/**
	 *
	 */
	public void resetScrollPosition() {
		// if we're in scrollback, scroll to bottom of window on input
		if (buffer.windowBase != buffer.screenBase)
			buffer.setWindowBase(buffer.screenBase);
	}
}
