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
import android.app.Activity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

/**
 * User interface {@link View} for showing a TerminalBridge in an
 * {@link Activity}. Handles drawing bitmap updates and passing keystrokes down
 * to terminal.
 *
 * @author jsharkey
 */
public class TerminalView extends View {

	private final Context context;
	public final TerminalBridge bridge;

	private Toast notification = null;
	private String lastNotification = null;
	private volatile boolean notifications = true;

	public TerminalView(Context context, TerminalBridge bridge) {
		super(context);

		this.context = context;
		this.bridge = bridge;

		setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		setFocusable(true);
		setFocusableInTouchMode(true);
	}

	public void destroy() {
		// tell bridge to destroy its bitmap
		bridge.parentDestroyed();
	}

	public void notifyUser(String message) {
		if (!notifications)
			return;

		if (notification != null) {
			// Don't keep telling the user the same thing.
			if (lastNotification != null && lastNotification.equals(message))
				return;

			notification.setText(message);
			notification.show();
		} else {
			notification = Toast.makeText(context, message, Toast.LENGTH_SHORT);
			notification.show();
		}

		lastNotification = message;
	}

	/**
	 * Sets the ability for the TerminalView to display Toast notifications to the user.
	 * @param value whether to enable notifications or not
	 */
	public void setNotifications(boolean value) {
		notifications = value;
	}

	@Override
	public boolean onCheckIsTextEditor() {
		return true;
	}

	@Override
	public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
		outAttrs.imeOptions |=
			EditorInfo.IME_FLAG_NO_EXTRACT_UI |
			EditorInfo.IME_FLAG_NO_ENTER_ACTION |
			EditorInfo.IME_ACTION_NONE;
		outAttrs.inputType = EditorInfo.TYPE_NULL;
		return new BaseInputConnection(this, false) {
			@Override
			public boolean deleteSurroundingText (int leftLength, int rightLength) {
				if (rightLength == 0 && leftLength == 0) {
					return this.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
				}
				for (int i = 0; i < leftLength; i++) {
					this.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
				}
				// TODO: forward delete
				return true;
			}
		};
	}
}
