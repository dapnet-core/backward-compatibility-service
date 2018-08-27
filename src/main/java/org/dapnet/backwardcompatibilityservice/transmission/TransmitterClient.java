package org.dapnet.backwardcompatibilityservice.transmission;

import io.netty.channel.Channel;
import org.dapnet.backwardcompatibilityservice.model.Transmitter;
import org.jgroups.stack.IpAddress;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.PriorityQueue;

/**
 * This class holds the client session.
 * 
 * @author Philipp Thiel
 */
final class TransmitterClient {

	public enum AckType {
		OK, RETRY, ERROR;
	}

	private final PriorityQueue<PagerMessage> messageQueue = new PriorityQueue<>();
	private final Channel channel;
	private int sequenceNumber;
	private Message currentMessage;
	private volatile Transmitter transmitter;

	/**
	 * Creates a new client session.
	 * 
	 * @param channel Client connection channel
	 * @throws NullPointerException If channel is null.
	 */
	public TransmitterClient(Channel channel) {
		if (channel == null) {
			throw new NullPointerException("channel");
		}

		this.channel = channel;
	}

	/**
	 * Returns the transmitter name if a transmitter instance is present or the
	 * short channel ID.
	 * 
	 * @return Transmitter name or short channel ID.
	 */
	public String getName() {
		Transmitter theTransmitter = transmitter;
		if (theTransmitter != null) {
			return theTransmitter.getName();
		} else {
			return channel.id().asShortText();
		}
	}

	/**
	 * Gets the transmitter data.
	 * 
	 * @return Transmitter data
	 */
	public Transmitter getTransmitter() {
		return transmitter;
	}

	/**
	 * Sets the transmitter data.
	 * 
	 * @param transmitter Transmitter data
	 */
	public void setTransmitter(Transmitter transmitter) {
		if (transmitter != null) {
			transmitter.setAddress(new IpAddress((InetSocketAddress) channel.remoteAddress()));
		}

		this.transmitter = transmitter;
	}


	/**
	 * Sends a message to the client.
	 * 
	 * @param msg Message to send.
	 */
	public void sendMessage(PagerMessage msg) {
		synchronized (messageQueue) {
			messageQueue.add(msg);
			sendNext(false);
		}
	}

	/**
	 * Sends all messages to the client.
	 * 
	 * @param messages Messages to send.
	 */
	public void sendMessages(Collection<PagerMessage> messages) {
		synchronized (messageQueue) {
			messageQueue.addAll(messages);
			sendNext(false);
		}
	}

	/**
	 * Acknowleges a message and sends the next one.
	 * 
	 * @param sequenceNumber Sequence number to ack.
	 * @param response       Ack response type.
	 */
	public boolean ackMessage(int sequenceNumber, AckType response) {
		synchronized (messageQueue) {
			if (currentMessage == null) {
				return false;
			}

			boolean valid = false;
			boolean retransmit = false;
			switch (response) {
			case OK:
				valid = currentMessage.getExpectedSequenceNumber() == sequenceNumber;
				currentMessage = null;
				break;
			case RETRY:
				valid = currentMessage.getSequenceNumber() == sequenceNumber;
				if (!currentMessage.retry()) {
					// Too many retries, discard message
					currentMessage = null;
				} else {
					retransmit = true;
				}
				break;
			case ERROR:
				valid = currentMessage.getSequenceNumber() == sequenceNumber;
				// Discard message
				currentMessage = null;
				break;
			}

			sendNext(retransmit);

			return valid;
		}
	}

	/**
	 * Returns the number of pending messages.
	 * 
	 * @return Number of pending messages.
	 */
	public int getPendingMessageCount() {
		synchronized (messageQueue) {
			return messageQueue.size();
		}
	}

	/**
	 * Closes the connection. This call will block until the connection is closed.
	 */
	public void close() {
		Channel theChannel = channel;
		if (theChannel != null) {
			theChannel.close().syncUninterruptibly();
		}
	}

	private int getNextSequenceNumber() {
		int sn = sequenceNumber;
		sequenceNumber = (sequenceNumber + 1) % 256;
		return sn;
	}

	private void sendNext(boolean retransmit) {
		if (currentMessage == null) {
			PagerMessage msg = messageQueue.poll();
			if (msg != null) {
				currentMessage = new Message(getNextSequenceNumber(), msg);
				channel.writeAndFlush(currentMessage);
			} else {
				return;
			}
		} else if (retransmit) {
			channel.writeAndFlush(currentMessage);
		}
	}

	/**
	 * This class wraps a non-transmitter message for transmission to a specific
	 * transmitter.
	 * 
	 * @author Philipp Thiel
	 */
	public static class Message {

		private static final int MAX_RETRY_COUNT = 5;
		private final int sequenceNumber;
		private final PagerMessage message;
		private int retryCount;

		public Message(int sequenceNumber, PagerMessage message) {
			this.sequenceNumber = sequenceNumber;
			this.message = message;
		}

		public int getSequenceNumber() {
			return sequenceNumber;
		}

		public int getExpectedSequenceNumber() {
			/* Warp around on 8 Bits */
			return ((sequenceNumber + 1) % 256);
		}

		public PagerMessage getMessage() {
			return message;
		}

		public boolean retry() {
			++retryCount;
			return retryCount < MAX_RETRY_COUNT;
		}
	}
}
