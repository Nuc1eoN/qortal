package org.qortal.network.message;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.data.network.OnlineAccountData;
import org.qortal.transform.Transformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For requesting online accounts info from remote peer, given our list of online accounts.
 *
 * Identical to V2, but added for consistency, since V2 will ultimately be phased out
 */
public class GetOnlineAccountsV3Message extends Message {
	private List<OnlineAccountData> onlineAccounts;
	private byte[] cachedData;

	public GetOnlineAccountsV3Message(List<OnlineAccountData> onlineAccounts) {
		this(-1, onlineAccounts);
	}

	private GetOnlineAccountsV3Message(int id, List<OnlineAccountData> onlineAccounts) {
		super(id, MessageType.GET_ONLINE_ACCOUNTS_V2);

		this.onlineAccounts = onlineAccounts;
	}

	public List<OnlineAccountData> getOnlineAccounts() {
		return this.onlineAccounts;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		int accountCount = bytes.getInt();

		List<OnlineAccountData> onlineAccounts = new ArrayList<>(accountCount);

		while (accountCount > 0) {
			long timestamp = bytes.getLong();

			for (int i = 0; i < accountCount; ++i) {
				byte[] publicKey = new byte[Transformer.PUBLIC_KEY_LENGTH];
				bytes.get(publicKey);

				onlineAccounts.add(new OnlineAccountData(timestamp, null, publicKey));
			}

			if (bytes.hasRemaining()) {
				accountCount = bytes.getInt();
			} else {
				// we've finished
				accountCount = 0;
			}
		}

		return new GetOnlineAccountsV3Message(id, onlineAccounts);
	}

	@Override
	protected synchronized byte[] toData() {
		if (this.cachedData != null)
			return this.cachedData;

		// Shortcut in case we have no online accounts
		if (this.onlineAccounts.isEmpty()) {
			this.cachedData = Ints.toByteArray(0);
			return this.cachedData;
		}

		// How many of each timestamp
		Map<Long, Integer> countByTimestamp = new HashMap<>();

		for (int i = 0; i < this.onlineAccounts.size(); ++i) {
			OnlineAccountData onlineAccountData = this.onlineAccounts.get(i);
			Long timestamp = onlineAccountData.getTimestamp();
			countByTimestamp.compute(timestamp, (k, v) -> v == null ? 1 : ++v);
		}

		// We should know exactly how many bytes to allocate now
		int byteSize = countByTimestamp.size() * (Transformer.INT_LENGTH + Transformer.TIMESTAMP_LENGTH)
				+ this.onlineAccounts.size() * Transformer.PUBLIC_KEY_LENGTH;

		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(byteSize);

			for (long timestamp : countByTimestamp.keySet()) {
				bytes.write(Ints.toByteArray(countByTimestamp.get(timestamp)));

				bytes.write(Longs.toByteArray(timestamp));

				for (int i = 0; i < this.onlineAccounts.size(); ++i) {
					OnlineAccountData onlineAccountData = this.onlineAccounts.get(i);

					if (onlineAccountData.getTimestamp() == timestamp)
						bytes.write(onlineAccountData.getPublicKey());
				}
			}

			this.cachedData = bytes.toByteArray();
			return this.cachedData;
		} catch (IOException e) {
			return null;
		}
	}

}
