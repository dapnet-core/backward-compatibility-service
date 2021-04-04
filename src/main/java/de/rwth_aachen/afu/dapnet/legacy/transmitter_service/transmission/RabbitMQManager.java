package de.rwth_aachen.afu.dapnet.legacy.transmitter_service.transmission;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class RabbitMQManager {
	private static final String RABBITMQHOST = "dapnetdc2.db0sda.ampr.org";
	private static final String RABBITMQUSER = "node-db0sda-dc2";
	private static final String RABBITMQPASSWORD = "73mxX4JLttzmVZ2";
	private Connection connection;
	private Channel channel;
	private Map<String, String> QueueMap = new HashMap<String, String>();
	private String ExchangeName;
	private TransmitterManager transmitterManager;

	public RabbitMQManager(String ExchangeName, TransmitterManager transmitterManager) throws Exception {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setUsername(RABBITMQUSER);
		factory.setPassword(RABBITMQPASSWORD);
//        factory.setVirtualHost(virtualHost);
		factory.setHost(RABBITMQHOST);
//        factory.setPort(portNumber);
		this.connection = factory.newConnection();
		this.channel = this.connection.createChannel();
		AMQP.Exchange.DeclareOk ExchangeResponse = this.channel.exchangeDeclarePassive(ExchangeName);
		this.ExchangeName = ExchangeName;
		this.transmitterManager = transmitterManager;
		System.out.println(ExchangeResponse.toString());

	}

	public boolean addRabbitMQQueue(String TransmitterName) throws Exception {
		if (this.QueueMap.containsKey(TransmitterName)) {
			System.out.println("Adding queue for transmitter " + TransmitterName + " failed, as it's already in list");
			return false;
		}

		Map<String, Object> args = new HashMap<String, Object>();
		args.put("x-expires", 1800000);
		AMQP.Queue.DeclareOk QueueDeclareResponse = this.channel.queueDeclare(TransmitterName, false, false, false,
				args);
		System.out.println(QueueDeclareResponse.toString());

		this.channel.queueBind(TransmitterName, this.ExchangeName, TransmitterName);
		String NewQueueName = QueueDeclareResponse.getQueue();
		this.QueueMap.put(TransmitterName, NewQueueName);

		Consumer consumer = new DefaultConsumer(this.channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
					byte[] body) throws IOException {
				String message = new String(body, StandardCharsets.UTF_8);
				System.out.println(" [x] Received '" + envelope.getRoutingKey() + "':'" + message + "'");
				/*
				 * { "priority": 3, "message": { "function": 0, "speed": 1200, "type":
				 * "numeric", "ric": 2504, "data": "205700   270818" }, "expires":
				 * "2018-08-28T03:57:00.007608Z", "protocol": "pocsag", "id":
				 * "b0a77459-b70b-408d-87f9-173831acbbe7" }
				 */

				String transmittername = envelope.getRoutingKey();
				JsonReader jsonReader = Json.createReader(new StringReader(message));
				JsonObject MessageObject = jsonReader.readObject();
				jsonReader.close();

				if (!MessageObject.getString("protocol").equals("pocsag")) {
					System.out.println("Not protocol ->pocsag<- in RabbitMQ Message");
					return;
				}

				// Generate Message and queue it
				PagerMessage pagerMessage = new PagerMessage(MessageObject.getJsonObject("message").getString("data"),
						MessageObject.getJsonObject("message").getInt("ric"), PagerMessage.MessagePriority.CALL,
						PagerMessage.FunctionalBits.values()[MessageObject.getJsonObject("message")
								.getInt("function")]);
				transmitterManager.sendMessage(pagerMessage, TransmitterName);
			}
		};
		this.channel.basicConsume(NewQueueName, true, consumer);
		return true;
	}

	public boolean pauseRabbitMQQueue(String Transmittername) throws Exception {
		if (!this.QueueMap.containsKey(Transmittername)) {
			System.out.println("Pausing queue for transmitter " + Transmittername + " failed, as it's not in list");
			return false;
		}
		String QueueName = this.QueueMap.get(Transmittername);
		this.channel.basicCancel(QueueName);
		return true;
	}
}
