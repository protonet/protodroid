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

import java.util.List;

import net.danopia.protonet.bean.ChannelBean;
import net.danopia.protonet.bean.HostBean;
import net.danopia.protonet.service.TerminalBridge;
import net.danopia.protonet.service.TerminalManager;
import net.danopia.protonet.util.HostDatabase;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.database.SQLException;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 * List all portForwards for a particular host and provide a way for users to add more portForwards,
 * edit existing portForwards, and delete portForwards.
 *
 * @author Kenny Root
 */
public class ChannelListActivity extends ListActivity {
	public final static String TAG = "ConnectBot.ChannelListActivity";

	private static final int LISTENER_CYCLE_TIME = 500;

	protected HostDatabase hostdb;

	private List<ChannelBean> channels;

	private ServiceConnection connection = null;
	protected TerminalBridge hostBridge = null;
	protected LayoutInflater inflater = null;

	private HostBean host;

	@Override
	public void onStart() {
		super.onStart();

		this.bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

		if(this.hostdb == null)
			this.hostdb = new HostDatabase(this);
	}

	@Override
	public void onStop() {
		super.onStop();

		this.unbindService(connection);

		if(this.hostdb != null) {
			this.hostdb.close();
			this.hostdb = null;
		}
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		long hostId = this.getIntent().getLongExtra(Intent.EXTRA_TITLE, -1);

		setContentView(R.layout.act_portforwardlist);

		// connect with hosts database and populate list
		this.hostdb = new HostDatabase(this);
		host = hostdb.findHostById(hostId);

		{
			final String nickname = host != null ? host.getNickname() : null;
			final Resources resources = getResources();

			if (nickname != null) {
				this.setTitle(String.format("%s: %s (%s)",
						resources.getText(R.string.app_name),
						resources.getText(R.string.title_port_forwards_list),
						nickname));
			} else {
				this.setTitle(String.format("%s: %s",
						resources.getText(R.string.app_name),
						resources.getText(R.string.title_port_forwards_list)));
			}
		}

		connection = new ServiceConnection() {
			public void onServiceConnected(ComponentName className, IBinder service) {
				TerminalManager bound = ((TerminalManager.TerminalBinder) service).getService();

				hostBridge = bound.getConnectedBridge(host);
				updateHandler.sendEmptyMessage(-1);
			}

			public void onServiceDisconnected(ComponentName name) {
				hostBridge = null;
			}
		};

		this.updateList();

		this.registerForContextMenu(this.getListView());

		this.getListView().setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
				ListView lv = ChannelListActivity.this.getListView();
				ChannelBean pfb = (ChannelBean) lv.getItemAtPosition(position);

				if (hostBridge != null) {
					if (pfb.isEnabled())
						hostBridge.disableChannel(pfb);
					else {
						if (!hostBridge.enableChannel(pfb))
							Toast.makeText(ChannelListActivity.this, getString(R.string.portforward_problem), Toast.LENGTH_LONG).show();
					}

					updateHandler.sendEmptyMessage(-1);
				}
			}
		});

		this.inflater = LayoutInflater.from(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuItem add = menu.add(R.string.portforward_menu_add);
		add.setIcon(android.R.drawable.ic_menu_add);
		add.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// build dialog to prompt user about updating
				final View portForwardView = inflater.inflate(R.layout.dia_portforward, null, false);

				new AlertDialog.Builder(ChannelListActivity.this)
					.setView(portForwardView)
					.setPositiveButton(R.string.portforward_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							try {
								final EditText nicknameEdit = (EditText) portForwardView.findViewById(R.id.nickname);
								final EditText uuidEdit = (EditText) portForwardView.findViewById(R.id.channel_uuid);

								ChannelBean pfb = new ChannelBean(
										host != null ? host.getId() : -1,
										nicknameEdit.getText().toString(),
										uuidEdit.getText().toString());

								if (hostBridge != null) {
									hostBridge.addChannel(pfb);
									hostBridge.enableChannel(pfb);
								}

								if (host != null && !hostdb.saveChannel(pfb))
									throw new SQLException("Could not save channel");

								updateHandler.sendEmptyMessage(-1);
							} catch (Exception e) {
								Log.e(TAG, "Could not update port forward", e);
								// TODO Show failure dialog.
							}
						}
					})
					.setNegativeButton(R.string.delete_neg, null).create().show();

				return true;
			}
		});

		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		// Create menu to handle deleting and editing port forward
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final ChannelBean pfb = (ChannelBean) this.getListView().getItemAtPosition(info.position);

		menu.setHeaderTitle(pfb.getNickname());

		MenuItem edit = menu.add(R.string.portforward_edit);
		edit.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				final View editTunnelView = inflater.inflate(R.layout.dia_portforward, null, false);

				final EditText nicknameEdit = (EditText) editTunnelView.findViewById(R.id.nickname);
				nicknameEdit.setText(pfb.getNickname());

				final EditText uuidEdit = (EditText) editTunnelView.findViewById(R.id.channel_uuid);
				uuidEdit.setText(String.valueOf(pfb.getUuid()));

				new AlertDialog.Builder(ChannelListActivity.this)
					.setView(editTunnelView)
					.setPositiveButton(R.string.button_change, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							try {
								if (hostBridge != null)
									hostBridge.disableChannel(pfb);

								pfb.setNickname(nicknameEdit.getText().toString());
								pfb.setUuid(uuidEdit.getText().toString());

								// Use the new settings for the existing connection.
								if (hostBridge != null)
									updateHandler.postDelayed(new Runnable() {
										public void run() {
											hostBridge.enableChannel(pfb);
											updateHandler.sendEmptyMessage(-1);
										}
									}, LISTENER_CYCLE_TIME);


								if (!hostdb.saveChannel(pfb))
									throw new SQLException("Could not save channel");

								updateHandler.sendEmptyMessage(-1);
							} catch (Exception e) {
								Log.e(TAG, "Could not update port forward", e);
								// TODO Show failure dialog.
							}
						}
					})
					.setNegativeButton(android.R.string.cancel, null).create().show();

				return true;
			}
		});

		MenuItem delete = menu.add(R.string.portforward_delete);
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(ChannelListActivity.this)
					.setMessage(getString(R.string.delete_message, pfb.getNickname()))
					.setPositiveButton(R.string.delete_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							try {
								// Delete the port forward from the host if needed.
								if (hostBridge != null)
									hostBridge.removeChannel(pfb);

								hostdb.deleteChannel(pfb);
							} catch (Exception e) {
								Log.e(TAG, "Could not delete channel", e);
							}

							updateHandler.sendEmptyMessage(-1);
						}
					})
					.setNegativeButton(R.string.delete_neg, null).create().show();

				return true;
			}
		});
	}

	protected Handler updateHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			ChannelListActivity.this.updateList();
		}
	};

	protected void updateList() {
		if (hostBridge != null) {
			this.channels = hostBridge.getChannels();
		} else {
			if (this.hostdb == null) return;
			this.channels = this.hostdb.getChannelsForHost(host);
		}

		PortForwardAdapter adapter = new PortForwardAdapter(this, channels);

		this.setListAdapter(adapter);
	}

	class PortForwardAdapter extends ArrayAdapter<ChannelBean> {
		class ViewHolder {
			public TextView nickname;
			public TextView caption;
		}

		private List<ChannelBean> portForwards;

		public PortForwardAdapter(Context context, List<ChannelBean> portForwards) {
			super(context, R.layout.item_portforward, portForwards);

			this.portForwards = portForwards;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;

			if (convertView == null) {
				convertView = inflater.inflate(R.layout.item_portforward, null, false);

				holder = new ViewHolder();
				holder.nickname = (TextView)convertView.findViewById(android.R.id.text1);
				holder.caption = (TextView)convertView.findViewById(android.R.id.text2);

				convertView.setTag(holder);
			} else
				holder = (ViewHolder) convertView.getTag();

			ChannelBean pfb = portForwards.get(position);
			holder.nickname.setText(pfb.getNickname());
			holder.caption.setText(pfb.getDescription());

			if (hostBridge != null && !pfb.isEnabled()) {
				holder.nickname.setPaintFlags(holder.nickname.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
				holder.caption.setPaintFlags(holder.caption.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			}

			return convertView;
		}
	}
}
