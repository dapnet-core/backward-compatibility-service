/*
 * DAPNET CORE PROJECT
 * Copyright (C) 2016
 *
 * Daniel Sialkowski
 *
 * daniel.sialkowski@rwth-aachen.de
 *
 * Institute of High Frequency Technology
 * RWTH AACHEN UNIVERSITY
 * Melatener Str. 25
 * 52074 Aachen
 */

package org.dapnet.backwardcompatibilityservice.transmission;

import org.dapnet.backwardcompatibilityservice.model.Transmitter;

public interface TransmitterManagerListener {
	void handleTransmitterStatusChanged(Transmitter transmitter);

	void handleDisconnectedFromAllTransmitters();

	Transmitter handleGetTransmitter(String name);
}
