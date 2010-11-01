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

package net.danopia.protonet.bean;

import net.danopia.protonet.util.HostDatabase;
import android.content.ContentValues;


/**
 * @author Kenny Root
 *
 */
public class ChannelBean extends AbstractBean {
	public static final String BEAN_NAME = "channel";

	/* Database fields */
	private long id = -1;
	private long hostId = -1;
	private String nickname = null;
	private String uuid = null;
	private String destAddr = null;
	private int destPort = -1;

	/* Transient values */
	private boolean enabled = false;
	private Object identifier = null;

	/**
	 * @param id database ID of port forward
	 * @param nickname Nickname to use to identify port forward
	 * @param type One of the port forward types from {@link HostDatabase}
	 * @param sourcePort Source port number
	 * @param destAddr Destination hostname or IP address
	 * @param destPort Destination port number
	 */
	public ChannelBean(long id, long hostId, String nickname, String uuid, String destAddr, int destPort) {
		this.id = id;
		this.hostId = hostId;
		this.nickname = nickname;
		this.uuid = uuid;
		this.destAddr = destAddr;
		this.destPort = destPort;
	}

	/**
	 * @param type One of the port forward types from {@link HostDatabase}
	 * @param source Source port number
	 * @param dest Destination is "host:port" format
	 */
	public ChannelBean(long hostId, String nickname, String uuid) {
		this.hostId = hostId;
		this.nickname = nickname;
		this.uuid = uuid;
	}

	@Override
	public String getBeanName() {
		return BEAN_NAME;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(long id) {
		this.id = id;
	}

	/**
	 * @return the id
	 */
	public long getId() {
		return id;
	}

	/**
	 * @param nickname the nickname to set
	 */
	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	/**
	 * @return the nickname
	 */
	public String getNickname() {
		return nickname;
	}

	/**
	 * @param type the type to set
	 */
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	/**
	 * @return the type
	 */
	public String getUuid() {
		return uuid;
	}

	/**
	 * @param dest The destination in "host:port" format
	 */
	public final void setDest(String dest) {
		String[] destSplit = dest.split(":");
		this.destAddr = destSplit[0];
		if (destSplit.length > 1)
			this.destPort = Integer.parseInt(destSplit[1]);
	}

	/**
	 * @param destAddr the destAddr to set
	 */
	public void setDestAddr(String destAddr) {
		this.destAddr = destAddr;
	}

	/**
	 * @return the destAddr
	 */
	public String getDestAddr() {
		return destAddr;
	}

	/**
	 * @param destPort the destPort to set
	 */
	public void setDestPort(int destPort) {
		this.destPort = destPort;
	}

	/**
	 * @return the destPort
	 */
	public int getDestPort() {
		return destPort;
	}

	/**
	 * @param enabled the enabled to set
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * @return the enabled
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * @param identifier the identifier of this particular type to set
	 */
	public void setIdentifier(Object identifier) {
		this.identifier = identifier;
	}

	/**
	 * @return the identifier used by this particular type
	 */
	public Object getIdentifier() {
		return identifier;
	}

	/**
	 * @return human readable description of the port forward
	 */
	public CharSequence getDescription() {
		return nickname;
	}

	/**
	 * @return
	 */
	@Override
	public ContentValues getValues() {
		ContentValues values = new ContentValues();

		values.put(HostDatabase.FIELD_CHANNEL_HOSTID, hostId);
		values.put(HostDatabase.FIELD_CHANNEL_NICKNAME, nickname);
		values.put(HostDatabase.FIELD_CHANNEL_UUID, uuid);
		values.put(HostDatabase.FIELD_CHANNEL_DESTADDR, destAddr);
		values.put(HostDatabase.FIELD_CHANNEL_DESTPORT, destPort);

		return values;
	}
}
