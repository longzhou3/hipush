package hipush.comet.protocol;

import java.util.ArrayList;
import java.util.List;

import hipush.core.ClientEnvironStat;
import hipush.core.Helpers;
import hipush.uuid.ClientId;
import hipush.uuid.TokenId;
import io.netty.buffer.ByteBuf;

public class Inputs {

	public static class AuthCommand extends ReadCommand {

		public AuthCommand(ByteBuf in) {
			super(in);
		}

		@Override
		public byte getType() {
			return MessageDefine.Read.CMD_AUTH;
		}

		private String clientId;
		private String token;

		@Override
		public void readImpl() {
			this.clientId = readStr();
			this.token = readStr();
		}

		public String getToken() {
			return token;
		}

		public String getClientId() {
			return clientId;
		}

		@Override
		public String getName() {
			return "auth";
		}

		@Override
		public boolean isValid() {
			return ClientId.isValid(clientId) && TokenId.isValid(token);
		}

	}

	public static class HeartBeatCommand extends ReadCommand {

		public HeartBeatCommand(ByteBuf in) {
			super(in);
		}

		@Override
		public byte getType() {
			return MessageDefine.Read.CMD_HEARTBEAT;
		}

		@Override
		public void readImpl() {

		}

		@Override
		public String getName() {
			return "heartbeat";
		}

		@Override
		public boolean isValid() {
			return true;
		}

	}

	public static class SubscribeCommand extends ReadCommand {

		public SubscribeCommand(ByteBuf in) {
			super(in);
		}

		@Override
		public byte getType() {
			return MessageDefine.Read.CMD_SUBSCRIBE;
		}

		private List<String> topics;

		@Override
		public void readImpl() {
			int len = readByte();
			topics = new ArrayList<String>(len);
			for (int i = 0; i < len; i++) {
				topics.add(readStr());
			}
		}

		public List<String> getTopics() {
			return topics;
		}

		@Override
		public String getName() {
			return "subscribe";
		}

		@Override
		public boolean isValid() {
			if (topics.isEmpty()) {
				return false;
			}
			for (String topic : topics) {
				if (!Helpers.isClientTopic(topic)) {
					return false;
				}
			}
			return true;
		}

	}

	public static class TopicListCommand extends ReadCommand {

		public TopicListCommand(ByteBuf in) {
			super(in);
		}

		@Override
		public byte getType() {
			return MessageDefine.Read.CMD_TOPIC_LIST;
		}

		@Override
		public void readImpl() {

		}

		@Override
		public String getName() {
			return "topic_list";
		}

		@Override
		public boolean isValid() {
			return true;
		}

	}

	public static class UnsubscribeCommand extends ReadCommand {

		public UnsubscribeCommand(ByteBuf in) {
			super(in);
		}

		@Override
		public byte getType() {
			return MessageDefine.Read.CMD_UNSUBSCRIBE;
		}

		private List<String> topics;

		@Override
		public void readImpl() {
			int len = readByte();
			topics = new ArrayList<String>(len);
			for (int i = 0; i < len; i++) {
				topics.add(readStr());
			}
		}

		public List<String> getTopics() {
			return topics;
		}

		@Override
		public String getName() {
			return "unsubscribe";
		}

		@Override
		public boolean isValid() {
			if (topics.isEmpty()) {
				return false;
			}
			for (String topic : topics) {
				if (!Helpers.isClientTopic(topic)) {
					return false;
				}
			}
			return true;
		}

	}

	public static class MessageListCommand extends ReadCommand {

		public MessageListCommand(ByteBuf in) {
			super(in);
		}

		@Override
		public byte getType() {
			return MessageDefine.Read.CMD_MESSAGE_LIST;
		}

		@Override
		public void readImpl() {

		}

		@Override
		public String getName() {
			return "get_messages";
		}

		@Override
		public boolean isValid() {
			return true;
		}

	}

	public static class MessageAckCommand extends ReadCommand {

		private List<String> messageIds;

		public MessageAckCommand(ByteBuf in) {
			super(in);
		}

		@Override
		public byte getType() {
			return MessageDefine.Read.CMD_MESSAGE_ACK;
		}

		@Override
		public void readImpl() {
			int len = readByte();
			messageIds = new ArrayList<String>(len);
			for (int i = 0; i < len; i++) {
				messageIds.add(readStr());
			}
		}

		public List<String> getMessageIds() {
			return messageIds;
		}

		@Override
		public String getName() {
			return "message_ack";
		}

		@Override
		public boolean isValid() {
			return true;
		}
	}

	public static class ReportEnvironCommand extends ReadCommand {

		private int networkType;
		private int isp;
		private String phoneType;
		private String[] extras;

		public ReportEnvironCommand(ByteBuf in) {
			super(in);
		}

		@Override
		public byte getType() {
			return MessageDefine.Read.CMD_REPORT_ENVIRON;
		}

		@Override
		public void readImpl() {
			this.networkType = readByte();
			this.isp = readByte();
			this.phoneType = readStr();
			extras = new String[readByte()];
			for (int i = 0; i < extras.length; i++) {
				extras[i] = readStr();
			}
		}

		public int getNetworkType() {
			return networkType;
		}

		public String getPhoneType() {
			return phoneType;
		}

		public int getIsp() {
			return isp;
		}

		public String[] getExtras() {
			return extras;
		}

		@Override
		public String getName() {
			return "report_environ";
		}

		@Override
		public boolean isValid() {
			return networkType >= ClientEnvironStat.NETWORK_TYPE_WIFI
					&& networkType <= ClientEnvironStat.NETWORK_TYPE_4G && isp >= ClientEnvironStat.ISP_EMPTY
					&& isp <= ClientEnvironStat.ISP_TELECOM;
		}

	}
	
	public static class ExchangeKeyCommand  extends ReadCommand {
		
		private byte[] encryptedSecretKey;

		public ExchangeKeyCommand(ByteBuf in) {
			super(in);
		}

		@Override
		public byte getType() {
			return MessageDefine.Read.CMD_EXCHANGE_KEY;
		}

		@Override
		public void readImpl() {
			this.encryptedSecretKey = this.readBytes();
		}

		@Override
		public String getName() {
			return "exchange_key";
		}

		@Override
		public boolean isValid() {
			return this.encryptedSecretKey.length == 64;
		}
		
		public byte[] getEncryptedSecretKey() {
			return this.encryptedSecretKey;
		}
		
	}
}
