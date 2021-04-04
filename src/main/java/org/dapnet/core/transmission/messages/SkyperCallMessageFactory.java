package org.dapnet.core.transmission.messages;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dapnet.core.model.Call;
import org.dapnet.core.model.CallSign;
import org.dapnet.core.model.CoreRepository;
import org.dapnet.core.model.ModelRepository;
import org.dapnet.core.model.Pager;
import org.dapnet.core.model.Pager.Type;
import org.dapnet.core.transmission.messages.PagerMessage.ContentType;
import org.dapnet.core.transmission.messages.PagerMessage.Priority;
import org.dapnet.core.transmission.messages.PagerMessage.SubAddress;

/**
 * Skyper call message factory.
 * 
 * @author Philipp Thiel
 */
class SkyperCallMessageFactory implements PagerMessageFactory<Call> {

	private static final Logger logger = LogManager.getLogger();
	private static final Pattern NUMERIC_PATTERN = Pattern.compile("[-Uu\\d\\(\\) ]+");
	private final CoreRepository repository;
	private final Function<String, String> encoder;

	/**
	 * Constructs a new Skyper call message factory.
	 * 
	 * @param repository Repository to use
	 * @param encoder    String encoder to use
	 */
	public SkyperCallMessageFactory(CoreRepository repository, Function<String, String> encoder) {
		this.repository = Objects.requireNonNull(repository, "Repository must not be null.");
		this.encoder = Objects.requireNonNull(encoder, "Encoder must not be null.");
	}

	@Override
	public Collection<PagerMessage> createMessage(Call payload) {
		final Priority priority = payload.isEmergency() ? Priority.EMERGENCY : Priority.CALL;
		final Instant now = Instant.now();

		final Collection<PagerMessage> messages = new LinkedList<>();
		final Lock lock = repository.getLock().readLock();
		lock.lock();

		try {
			// Test if message is numeric
			Matcher m = NUMERIC_PATTERN.matcher(payload.getText());
			boolean numeric = m.matches();

			final ModelRepository<CallSign> callsigns = repository.getCallSigns();

			for (String name : payload.getCallSignNames()) {
				CallSign callsign = callsigns.get(name);
				if (callsign == null) {
					logger.error("Callsign does not exist: {}", name);
					continue;
				}

				ContentType type;
				SubAddress mode;
				String text;
				if (!callsign.isNumeric()) {
					// Support for alphanumeric messages -> create ALPHANUM message
					mode = SubAddress.ADDR_D;
					type = ContentType.ALPHANUMERIC;
					text = encoder.apply(payload.getText());
				} else if (numeric) {
					// No support for alphanumeric messages but text is numeric -> create NUMERIC
					// message
					mode = SubAddress.ADDR_A;
					type = ContentType.NUMERIC;
					text = payload.getText().toUpperCase();
				} else {
					// No support for alphanumeric messages and non-numeric message -> skip
					logger.warn("Callsign {} does not support alphanumeric messages.", callsign.getName());
					continue;
				}

				for (Pager pager : callsign.getPagers()) {
					if (pager.getType() == Type.SKYPER) {
						PagerMessage message = new PagerMessage(now, priority, pager.getNumber(), mode, type, text);
						messages.add(message);
					}
				}
			}
		} finally {
			lock.unlock();
		}

		return Collections.unmodifiableCollection(messages);
	}

}
