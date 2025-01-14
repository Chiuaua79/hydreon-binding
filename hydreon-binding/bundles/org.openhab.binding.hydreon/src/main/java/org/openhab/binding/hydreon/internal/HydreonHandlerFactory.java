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
package org.openhab.binding.hydreon.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hydreon.internal.handler.HydreonSensorHandler;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HydreonHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Cor Hoogendoorn - Initial contribution
 */
@NonNullByDefault
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.hydreon")
public class HydreonHandlerFactory extends BaseThingHandlerFactory {
    private Logger logger = LoggerFactory.getLogger(HydreonHandlerFactory.class);

    private final SerialPortManager serialPortManager;

    @Activate
    public HydreonHandlerFactory(final @Reference SerialPortManager serialPortManager) {
        this.serialPortManager = serialPortManager;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return HydreonSensorHandler.SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        logger.debug("Hydreon thing factory: createHandler {} of type {}", thing.getThingTypeUID(), thing.getUID());

        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (HydreonSensorHandler.SUPPORTED_THING_TYPES.contains(thingTypeUID)) {
            return new HydreonSensorHandler(thing, serialPortManager);
        }

        return null;
    }
}
