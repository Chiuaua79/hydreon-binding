/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.hydreon.internal.handler;

import static org.openhab.binding.hydreon.internal.HydreonBindingConstants.*;
import static org.openhab.core.library.unit.SIUnits.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;
import java.util.TooManyListenersException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortEvent;
import org.openhab.core.io.transport.serial.SerialPortEventListener;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.io.transport.serial.UnsupportedCommOperationException;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HydreonSensorHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Cor Hoogendoorn - Initial contribution
 */
public class HydreonSensorHandler extends BaseThingHandler {
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_RG9);

    private final Logger logger = LoggerFactory.getLogger(HydreonSensorHandler.class);

    private static final int RECEIVE_TIMEOUT = 3000;

    private SerialPort serialPort;
    private final SerialPortManager serialPortManager;
    private ReceiveThread receiveThread;
    private int intensityLevel;
    private int holdTime = -1;
    private boolean disableLed;

    private ScheduledFuture<?> offlineTimerJob;
    private ScheduledFuture<?> switchResetTimerJob;

    private Date lastData;

    public HydreonSensorHandler(Thing thing, SerialPortManager serialPortManager) {
        super(thing);
        this.serialPortManager = serialPortManager;
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Hydreon Sensor handler.");

        updateStatus(ThingStatus.UNKNOWN);

        Configuration config = getThing().getConfiguration();

        intensityLevel = ((BigDecimal) getConfig().get(PARAMETER_INTENSITY_LEVEL)).intValue();
        if (getConfig().get(PARAMETER_HOLD_TIME) != null) {
            holdTime = ((BigDecimal) getConfig().get(PARAMETER_HOLD_TIME)).intValue();
        }
        disableLed = (Boolean) getConfig().get(PARAMETER_DISABLE_LED);

        final String port = (String) config.get("port");

        Runnable pollingRunnable = () -> {
            if (connectPort(port)) {
                offlineTimerJob.cancel(true);
            }
        };

        offlineTimerJob = scheduler.scheduleWithFixedDelay(pollingRunnable, 0, 60, TimeUnit.SECONDS);

        updateState(getChannelID(CHANNEL_GROUP_INTERACTION, CHANNEL_KILL), OnOffType.from(false));
    }

    @Override
    public void dispose() {
        disconnect();
        if (offlineTimerJob != null) {
            offlineTimerJob.cancel(true);
        }
        if (switchResetTimerJob != null) {
            switchResetTimerJob.cancel(true);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(getChannelID(CHANNEL_GROUP_INTERACTION, CHANNEL_KILL))
                && command.equals(OnOffType.ON)) {
            updateState(getChannelID(CHANNEL_GROUP_INTERACTION, CHANNEL_KILL), OnOffType.from(true));
            sendToHydreon("K");
            resetSwitch();
        }
    }

    /**
     * Connects to the comm port and starts send and receive threads.
     *
     * @param serialPortName the port name to open
     * @throws SerialInterfaceException when a connection error occurs.
     */
    private boolean connectPort(final String serialPortName) {
        logger.debug("Hydreon Connecting to serial port {}", serialPortName);

        SerialPortIdentifier portIdentifier = serialPortManager.getIdentifier(serialPortName);
        if (portIdentifier == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Serial Error: Port " + serialPortName + " does not exist");
            return false;
        }

        boolean success = false;
        try {
            serialPort = portIdentifier.open("org.openhab.binding.hydreon", 2000);
            serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            serialPort.enableReceiveThreshold(1);
            serialPort.enableReceiveTimeout(RECEIVE_TIMEOUT);

            receiveThread = new ReceiveThread();
            receiveThread.start();

            // RXTX serial port library causes high CPU load
            // Start event listener, which will just sleep and slow down event loop
            serialPort.addEventListener(this.receiveThread);
            serialPort.notifyOnDataAvailable(true);

            logger.debug("Serial port is initialized");

            success = true;
        } catch (PortInUseException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Serial Error: Port " + serialPortName + " in use");
        } catch (UnsupportedCommOperationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Serial Error: Unsupported comm operation on port " + serialPortName);
        } catch (TooManyListenersException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Serial Error: Too many listeners on port " + serialPortName);
        }

        return success;
    }

    /**
     * Disconnects from the serial interface and stops send and receive threads.
     */
    private void disconnect() {
        if (receiveThread != null) {
            receiveThread.interrupt();
            try {
                receiveThread.join();
            } catch (InterruptedException e) {
            }
            receiveThread = null;
        }

        if (this.serialPort != null) {
            this.serialPort.close();
            this.serialPort = null;
        }
        logger.debug("Disconnected from serial port");
    }

    private void sendToHydreon(String string) {
        try {
            synchronized (serialPort.getOutputStream()) {
                serialPort.getOutputStream().write(string.getBytes());
                serialPort.getOutputStream().write(10);
                serialPort.getOutputStream().flush();
            }
        } catch (IOException e) {
            logger.error("Got I/O exception {} during sending. exiting thread.", e.getLocalizedMessage());
        }
    }

    private class ReceiveThread extends Thread implements SerialPortEventListener {
        private final Logger logger = LoggerFactory.getLogger(ReceiveThread.class);

        @Override
        public void serialEvent(SerialPortEvent arg0) {
            try {
                logger.trace("RXTX library CPU load workaround, sleep forever");
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
            }
        }

        /**
         * Run method. Runs the actual receiving process.
         */
        @Override
        public void run() {
            logger.debug("Starting Hydreon Receive Thread");
            byte[] rxPacket = new byte[275];
            int rxCnt = 0;
            int rxByte;
            while (!interrupted()) {
                try {
                    rxByte = serialPort.getInputStream().read();

                    if (rxByte == -1) {
                        continue;
                    }

                    lastData = new Date();
                    startTimeoutCheck();

                    // Check for end of line
                    if (rxByte == 13 && rxCnt > 0) {
                        String inputString = new String(rxPacket, 0, rxCnt);
                        logger.debug("Hydreon received: {}", inputString);
                        String[] p = inputString.split("\\s+");

                        switch (p[0]) {
                            case "DIP": // Physical dip switch positions
                                updateState(getChannelID(CHANNEL_GROUP_DEVICEINFO, CHANNEL_DIP_SWITCH),
                                        new StringType(p[1]));
                                break;
                            case "PwrDays": // Amount of days without reset
                                int powerDays = Integer.parseInt(p[1]);
                                updateState(getChannelID(CHANNEL_GROUP_DEVICEINFO, CHANNEL_POWER_DAYS),
                                        new DecimalType(powerDays));
                                break;
                            case "R": // Rain intensity
                                int rainIntensity = Integer.parseInt(p[1]);
                                if (rainIntensity >= 0 && rainIntensity <= 7) {
                                    updateState(getChannelID(CHANNEL_GROUP_SENSORS, CHANNEL_RAIN_INTENSITY),
                                            new DecimalType(rainIntensity));
                                }
                                break;
                            case "Reset": // Reset reason
                                updateState(getChannelID(CHANNEL_GROUP_DEVICEINFO, CHANNEL_RESET),
                                        new StringType(p[1]));
                                sendToHydreon("i" + intensityLevel);
                                if (holdTime != -1) {
                                    sendToHydreon("h" + holdTime);
                                }
                                if (disableLed == true) {
                                    sendToHydreon("d 1");
                                } else {
                                    sendToHydreon("d 0");
                                }
                                break;
                            case "t": // Temperature
                                String temperatureString = new String(p[2]);
                                BigDecimal temperature = new BigDecimal(
                                        temperatureString.substring(0, temperatureString.length() - 1));
                                updateState(getChannelID(CHANNEL_GROUP_SENSORS, CHANNEL_TEMPERATURE),
                                        new QuantityType<>(temperature.setScale(1), CELSIUS));
                                break;
                            default:
                                if (p.length < 3) {
                                    logger.debug("Hydreon sensor: short data ({})", p.length);
                                    break;
                                }
                                break;
                        }

                        updateStatus(ThingStatus.ONLINE);

                        rxCnt = 0;
                    } else if (rxByte != 10) {
                        // Ignore line feed
                        rxPacket[rxCnt] = (byte) rxByte;

                        if (rxCnt < rxPacket.length) {
                            rxCnt++;
                        }
                    }
                } catch (Exception e) {
                    rxCnt = 0;
                    logger.error("Exception during Hydreon receive thread", e);
                }
            }

            logger.debug("Stopping Hydreon Receive Thread");
            serialPort.removeEventListener();
        }
    }

    private synchronized void startTimeoutCheck() {
        Runnable pollingRunnable = () -> {
            String detail;
            if (lastData == null) {
                detail = "No data received";
            } else {
                detail = "No data received since " + lastData.toString();
            }
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, detail);
        };

        if (offlineTimerJob != null) {
            offlineTimerJob.cancel(true);
        }

        offlineTimerJob = scheduler.schedule(pollingRunnable, 300, TimeUnit.SECONDS);
    }

    private synchronized void resetSwitch() {
        Runnable switchResetter = () -> {
            updateState(getChannelID(CHANNEL_GROUP_INTERACTION, CHANNEL_KILL), OnOffType.from(false));
        };

        if (switchResetTimerJob != null) {
            switchResetTimerJob.cancel(true);
        }

        switchResetTimerJob = scheduler.schedule(switchResetter, 1, TimeUnit.SECONDS);
    }

    /**
     * Get ChannelID including group
     *
     * @param group String channel-group
     * @param channel String channel-name
     * @return String channelID
     */
    protected String getChannelID(String group, String channel) {
        return group + "#" + channel;
    }
}
