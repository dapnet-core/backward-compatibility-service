package org.dapnet.backwardcompatibilityservice.transmission;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class RabbitMQManager {
    private static final String RABBITMQHOST = "dapnetdc2.db0sda.ampr.org";
    private static final String RABBITMQUSER = "node-db0sda-dc2";
    private static final String RABBITMQPASSWORD = "73mxX4JLttzmVZ2";
    private Connection connection;
    private Channel channel;
    private Map<String, String> QueueMap = new HashMap<String, String>();
    private String ExchangeName;

    public RabbitMQManager (String ExchangeName) throws Exception {
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
        System.out.println(ExchangeResponse.toString());
    }

    public boolean addRabbitMQQueue (String TransmitterName) throws Exception {
        if (this.QueueMap.containsKey(TransmitterName)) {
            System.out.println(
                    "Adding queue for transmitter " + TransmitterName + " failed, as it's already in list");
            return false;
        }

        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", 1800000);
        AMQP.Queue.DeclareOk QueueDeclareResponse =
                this.channel.queueDeclare(TransmitterName,false,false, false, args);
        System.out.println(QueueDeclareResponse.toString());

        this.channel.queueBind(TransmitterName, this.ExchangeName,TransmitterName);
        String NewQueueName = QueueDeclareResponse.getQueue();
        this.QueueMap.put(TransmitterName, NewQueueName);

        Consumer consumer = new DefaultConsumer(this.channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println(" [x] Received '" + envelope.getRoutingKey() + "':'" + message + "'");
            }
        };
        this.channel.basicConsume(NewQueueName, true, consumer);
        return true;
    }

    public boolean pauseRabbitMQQueue (String Transmittername) throws Exception {
        if (!this.QueueMap.containsKey(Transmittername)) {
            System.out.println(
                    "Pausing queue for transmitter " + Transmittername + " failed, as it's not in list");
            return false;
        }
        String QueueName = this.QueueMap.get(Transmittername);
        this.channel.basicCancel(QueueName);
        return true;
    }
}
