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

package net.danopia.protonet;

import net.danopia.protonet.service.TerminalBridge;
import net.danopia.protonet.service.TerminalManager;
import net.danopia.protonet.util.PreferenceConstants;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.nullwire.trace.ExceptionHandler;

public class ConsoleActivity extends Activity {
	public final static String TAG = "ConnectBot.ConsoleActivity";

	protected static final int REQUEST_EDIT = 1;

	// Direction to shift the ViewFlipper
	private static final int SHIFT_LEFT = 0;
	private static final int SHIFT_RIGHT = 1;

	protected ViewFlipper flip = null;
	protected TerminalManager bound = null;
	protected LayoutInflater inflater = null;

	private SharedPreferences prefs = null;

	protected Uri requested;

	private TextView empty;

	private Animation slide_left_in, slide_left_out, slide_right_in, slide_right_out, fade_stay_hidden, fade_out_delayed;

	private float lastX, lastY;

	private MenuItem disconnect, channel;

	private int lastTouchRow, lastTouchCol;

	private Handler handler = new Handler();

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// let manager know about our event handling services
			bound.disconnectHandler = disconnectHandler;

			Log.d(TAG, String.format("Connected to TerminalManager and found bridges.size=%d", bound.bridges.size()));

			// clear out any existing bridges and record requested index
			flip.removeAllViews();

			final String requestedNickname = (requested != null) ? requested.getFragment() : null;
			int requestedIndex = 0;

			TerminalBridge requestedBridge = bound.getConnectedBridge(requestedNickname);

			// If we didn't find the requested connection, try opening it
			if (requestedNickname != null && requestedBridge == null) {
				try {
					Log.d(TAG, String.format("We couldnt find an existing bridge with URI=%s (nickname=%s), so creating one now", requested.toString(), requestedNickname));
					requestedBridge = bound.openConnection(requested);
				} catch(Exception e) {
					Log.e(TAG, "Problem while trying to create new requested bridge from URI", e);
				}
			}

			// create views for all bridges on this service
			for (TerminalBridge bridge : bound.bridges) {

				final int currentIndex = addNewTerminalView(bridge);

				// check to see if this bridge was requested
				if (bridge == requestedBridge)
					requestedIndex = currentIndex;
			}

			setDisplayedTerminal(requestedIndex);
		}

		public void onServiceDisconnected(ComponentName className) {
			// tell each bridge to forget about our prompt handler
			synchronized (bound.bridges) {
				for(TerminalBridge bridge : bound.bridges)
					bridge.promptHelper.setHandler(null);
			}

			flip.removeAllViews();
			updateEmptyVisible();
			bound = null;
		}
	};

	protected Handler disconnectHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Log.d(TAG, "Someone sending HANDLE_DISCONNECT to parentHandler");

			// someone below us requested to display a password dialog
			// they are sending nickname and requested
			TerminalBridge bridge = (TerminalBridge)msg.obj;

			if (bridge.isAwaitingClose())
				closeBridge(bridge);
		}
	};

	/**
	 * @param bridge
	 */
	private void closeBridge(final TerminalBridge bridge) {
		synchronized (flip) {
			final int flipIndex = getFlipIndex(bridge);

			if (flipIndex >= 0) {
				if (flip.getDisplayedChild() == flipIndex) {
					shiftCurrentTerminal(SHIFT_LEFT);
				}
				flip.removeViewAt(flipIndex);

				/* TODO Remove this workaround when ViewFlipper is fixed to listen
				 * to view removals. Android Issue 1784
				 */
				final int numChildren = flip.getChildCount();
				if (flip.getDisplayedChild() >= numChildren &&
						numChildren > 0) {
					flip.setDisplayedChild(numChildren - 1);
				}

				updateEmptyVisible();
			}

			// If we just closed the last bridge, go back to the previous activity.
			if (flip.getChildCount() == 0) {
				finish();
			}
		}
	}

	protected View findCurrentView(int id) {
		View view = flip.getCurrentView();
		if(view == null) return null;
		return view.findViewById(id);
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		this.setContentView(R.layout.act_console);

		ExceptionHandler.register(this);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		// hide status bar if requested by user
		if (prefs.getBoolean(PreferenceConstants.FULLSCREEN, false)) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		// TODO find proper way to disable volume key beep if it exists.
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		// handle requested console from incoming intent
		requested = getIntent().getData();

		inflater = LayoutInflater.from(this);

		flip = (ViewFlipper)findViewById(R.id.console_flip);
		empty = (TextView)findViewById(android.R.id.empty);

		// preload animations for terminal switching
		slide_left_in = AnimationUtils.loadAnimation(this, R.anim.slide_left_in);
		slide_left_out = AnimationUtils.loadAnimation(this, R.anim.slide_left_out);
		slide_right_in = AnimationUtils.loadAnimation(this, R.anim.slide_right_in);
		slide_right_out = AnimationUtils.loadAnimation(this, R.anim.slide_right_out);

		// detect fling gestures to switch between terminals
		final GestureDetector detect = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
			private float totalY = 0;

			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

				final float distx = e2.getRawX() - e1.getRawX();
				final float disty = e2.getRawY() - e1.getRawY();
				final int goalwidth = flip.getWidth() / 2;

				// need to slide across half of display to trigger console change
				// make sure user kept a steady hand horizontally
				if (Math.abs(disty) < (flip.getHeight() / 4)) {
					if (distx > goalwidth) {
						shiftCurrentTerminal(SHIFT_RIGHT);
						return true;
					}

					if (distx < -goalwidth) {
						shiftCurrentTerminal(SHIFT_LEFT);
						return true;
					}

				}

				return false;
			}


			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

				if (e1 == null || e2 == null)
					return false;

				// if releasing then reset total scroll
				if (e2.getAction() == MotionEvent.ACTION_UP) {
					totalY = 0;
				}

				// activate consider if within x tolerance
				if (Math.abs(e1.getX() - e2.getX()) < ViewConfiguration.getTouchSlop() * 4) {

					/*
					View flip = findCurrentView(R.id.console_flip);
					if(flip == null) return false;
					TerminalView terminal = (TerminalView)flip;

					// estimate how many rows we have scrolled through
					// accumulate distance that doesn't trigger immediate scroll
					totalY += distanceY;
					final int moved = (int)(totalY / terminal.bridge.charHeight);

					// consume as scrollback only if towards right half of screen
					if (e2.getX() > flip.getWidth() / 2) {
						if (moved != 0) {
							int base = terminal.bridge.buffer.getWindowBase();
							terminal.bridge.buffer.setWindowBase(base + moved);
							totalY = 0;
							return true;
						}
					} else {
						// otherwise consume as pgup/pgdown for every 5 lines
						if (moved > 5) {
							((vt320)terminal.bridge.buffer).keyPressed(vt320.KEY_PAGE_DOWN, ' ', 0);
							terminal.bridge.tryKeyVibrate();
							totalY = 0;
							return true;
						} else if (moved < -5) {
							((vt320)terminal.bridge.buffer).keyPressed(vt320.KEY_PAGE_UP, ' ', 0);
							terminal.bridge.tryKeyVibrate();
							totalY = 0;
							return true;
						}

					}
					*/

				}

				return false;
			}


		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		View view = findCurrentView(R.id.console_flip);
		final boolean activeTerminal = (view instanceof TerminalView);
		boolean sessionOpen = false;
		boolean disconnected = false;

		if (activeTerminal) {
			TerminalBridge bridge = ((TerminalView) view).bridge;
			sessionOpen = bridge.isSessionOpen();
			disconnected = bridge.isDisconnected();
		}

		menu.setQwertyMode(true);

		disconnect = menu.add(R.string.list_host_disconnect);
		disconnect.setAlphabeticShortcut('w');
		if (!sessionOpen && disconnected)
			disconnect.setTitle(R.string.console_menu_close);
		disconnect.setEnabled(activeTerminal);
		disconnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		disconnect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// disconnect or close the currently visible session
				TerminalView terminalView = (TerminalView) findCurrentView(R.id.console_flip);
				TerminalBridge bridge = terminalView.bridge;

				bridge.dispatchDisconnect(true);
				return true;
			}
		});

		channel = menu.add(R.string.console_menu_channels);
		channel.setAlphabeticShortcut('f');
		channel.setIcon(android.R.drawable.ic_menu_manage);
		channel.setEnabled(sessionOpen);
		channel.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				TerminalView terminalView = (TerminalView) findCurrentView(R.id.console_flip);
				TerminalBridge bridge = terminalView.bridge;

				Intent intent = new Intent(ConsoleActivity.this, ChannelListActivity.class);
				intent.putExtra(Intent.EXTRA_TITLE, bridge.host.getId());
				ConsoleActivity.this.startActivityForResult(intent, REQUEST_EDIT);
				return true;
			}
		});

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		setVolumeControlStream(AudioManager.STREAM_NOTIFICATION);

		final View view = findCurrentView(R.id.console_flip);
		boolean activeTerminal = (view instanceof TerminalView);
		boolean sessionOpen = false;
		boolean disconnected = false;

		if (activeTerminal) {
			TerminalBridge bridge = ((TerminalView) view).bridge;
			sessionOpen = bridge.isSessionOpen();
			disconnected = bridge.isDisconnected();
		}

		disconnect.setEnabled(activeTerminal);
		if (sessionOpen || !disconnected)
			disconnect.setTitle(R.string.list_host_disconnect);
		else
			disconnect.setTitle(R.string.console_menu_close);
		channel.setEnabled(sessionOpen);

		return true;
	}

	@Override
	public void onOptionsMenuClosed(Menu menu) {
		super.onOptionsMenuClosed(menu);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}

	@Override
	public void onStart() {
		super.onStart();

		// connect with manager service to find all bridges
		// when connected it will insert all views
		bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "onPause called");
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "onResume called");

		// Make sure we don't let the screen fall asleep.
		// This also keeps the Wi-Fi chipset from disconnecting us.
		if (prefs.getBoolean(PreferenceConstants.KEEP_ALIVE, true)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onNewIntent(android.content.Intent)
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		Log.d(TAG, "onNewIntent called");

		requested = intent.getData();

		if (requested == null) {
			Log.e(TAG, "Got null intent data in onNewIntent()");
			return;
		}

		if (bound == null) {
			Log.e(TAG, "We're not bound in onNewIntent()");
			return;
		}

		TerminalBridge requestedBridge = bound.getConnectedBridge(requested.getFragment());
		int requestedIndex = 0;

		synchronized (flip) {
			if (requestedBridge == null) {
				// If we didn't find the requested connection, try opening it

				try {
					Log.d(TAG, String.format("We couldnt find an existing bridge with URI=%s (nickname=%s),"+
							"so creating one now", requested.toString(), requested.getFragment()));
					requestedBridge = bound.openConnection(requested);
				} catch(Exception e) {
					Log.e(TAG, "Problem while trying to create new requested bridge from URI", e);
				}

				requestedIndex = addNewTerminalView(requestedBridge);
			} else {
				final int flipIndex = getFlipIndex(requestedBridge);
				if (flipIndex > requestedIndex) {
					requestedIndex = flipIndex;
				}
			}

			setDisplayedTerminal(requestedIndex);
		}
	}

	@Override
	public void onStop() {
		super.onStop();

		unbindService(connection);
	}

	protected void shiftCurrentTerminal(final int direction) {
		View overlay;
		synchronized (flip) {
			boolean shouldAnimate = flip.getChildCount() > 1;

			// Only show animation if there is something else to go to.
			if (shouldAnimate) {
				// keep current overlay from popping up again
				overlay = findCurrentView(R.id.terminal_overlay);
				if (overlay != null)
					overlay.startAnimation(fade_stay_hidden);

				if (direction == SHIFT_LEFT) {
					flip.setInAnimation(slide_left_in);
					flip.setOutAnimation(slide_left_out);
					flip.showNext();
				} else if (direction == SHIFT_RIGHT) {
					flip.setInAnimation(slide_right_in);
					flip.setOutAnimation(slide_right_out);
					flip.showPrevious();
				}
			}

			ConsoleActivity.this.updateDefault();

			if (shouldAnimate) {
				// show overlay on new slide and start fade
				overlay = findCurrentView(R.id.terminal_overlay);
				if (overlay != null)
					overlay.startAnimation(fade_out_delayed);
			}
		}
	}

	/**
	 * Save the currently shown {@link TerminalView} as the default. This is
	 * saved back down into {@link TerminalManager} where we can read it again
	 * later.
	 */
	private void updateDefault() {
		// update the current default terminal
		View view = findCurrentView(R.id.console_flip);
		if(!(view instanceof TerminalView)) return;

		TerminalView terminal = (TerminalView)view;
		if(bound == null) return;
		bound.defaultBridge = terminal.bridge;
	}

	protected void updateEmptyVisible() {
		// update visibility of empty status message
		empty.setVisibility((flip.getChildCount() == 0) ? View.VISIBLE : View.GONE);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		Log.d(TAG, String.format("onConfigurationChanged; requestedOrientation=%d, newConfig.orientation=%d", getRequestedOrientation(), newConfig.orientation));
		if (bound != null) {
			bound.hardKeyboardHidden = (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES);
		}
	}

	/**
	 * Adds a new TerminalBridge to the current set of views in our ViewFlipper.
	 *
	 * @param bridge TerminalBridge to add to our ViewFlipper
	 * @return the child index of the new view in the ViewFlipper
	 */
	private int addNewTerminalView(TerminalBridge bridge) {
		// inflate each terminal view
		RelativeLayout view = (RelativeLayout)inflater.inflate(R.layout.item_terminal, flip, false);

		// set the terminal overlay text
		TextView overlay = (TextView)view.findViewById(R.id.terminal_overlay);
		overlay.setText(bridge.host.getNickname());

		// and add our terminal view control, using index to place behind overlay
		TerminalView terminal = new TerminalView(ConsoleActivity.this, bridge);
		terminal.setId(R.id.console_flip);
		view.addView(terminal, 0);

		synchronized (flip) {
			// finally attach to the flipper
			flip.addView(view);
			return flip.getChildCount() - 1;
		}
	}

	private int getFlipIndex(TerminalBridge bridge) {
		synchronized (flip) {
			final int children = flip.getChildCount();
			for (int i = 0; i < children; i++) {
				final View view = flip.getChildAt(i).findViewById(R.id.console_flip);

				if (view == null || !(view instanceof TerminalView)) {
					// How did that happen?
					continue;
				}

				final TerminalView tv = (TerminalView) view;

				if (tv.bridge == bridge) {
					return i;
				}
			}
		}

		return -1;
	}

	/**
	 * Displays the child in the ViewFlipper at the requestedIndex and updates the prompts.
	 *
	 * @param requestedIndex the index of the terminal view to display
	 */
	private void setDisplayedTerminal(int requestedIndex) {
		synchronized (flip) {
			try {
				// show the requested bridge if found, also fade out overlay
				flip.setDisplayedChild(requestedIndex);
				flip.getCurrentView().findViewById(R.id.terminal_overlay)
						.startAnimation(fade_out_delayed);
			} catch (NullPointerException npe) {
				Log.d(TAG, "View went away when we were about to display it", npe);
			}

			updateEmptyVisible();
		}
	}
}
