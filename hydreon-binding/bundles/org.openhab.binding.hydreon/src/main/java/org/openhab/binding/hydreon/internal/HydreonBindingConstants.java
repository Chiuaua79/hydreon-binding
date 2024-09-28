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
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link HydreonBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Cor Hoogendoorn - Initial contribution
 */
@NonNullByDefault
public class HydreonBindingConstants {

    public static final String BINDING_ID = "hydreon";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_RG9 = new ThingTypeUID(BINDING_ID, "hydreon_rg9");

    // List of all Channel ids
    public static final String CHANNEL_GROUP_SENSORS = "sensors";
    public static final String CHANNEL_RAIN_INTENSITY = "rain-intensity";
    public static final String CHANNEL_TEMPERATURE = "temperature";
    public static final String CHANNEL_GROUP_INTERACTION = "interaction";
    public static final String CHANNEL_KILL = "kill";
    public static final String CHANNEL_GROUP_DEVICEINFO = "device-info";
    public static final String CHANNEL_RESET = "reset";
    public static final String CHANNEL_POWER_DAYS = "power-days";
    public static final String CHANNEL_DIP_SWITCH = "dip-switch";

    // List of parameters
    public static final String PARAMETER_INTENSITY_LEVEL = "intensityLevel";
    public static final String PARAMETER_HOLD_TIME = "holdTime";
    public static final String PARAMETER_DISABLE_LED = "disableLed";
}
