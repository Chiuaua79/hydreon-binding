# Hydreon Binding

This is the binding for the [Hydreon](https://rainsensors.com/) optical rain sensors.
Contrary to the usual rain sensors, the Hydreon rain sensors use a lens and its refraction to detect rain drops.

Assumption is that the RG-9 sensor is directly connected to a serial port of the server running openHab. Another (and more logical) option is of course by connecting it to the serial port of an ESP board running ser2net (like ESPEasy) and your server connecting to the board via socat, creating a local serial port.

The reason for development of this binding is that the device for some reason locks up the Serial binding Thing every once in a while, which makes the receipt of rain indications unreliable. This binding uses a slightly different approach for the serial connection, which is copied from the [Meteostick binding](https://www.openhab.org/addons/bindings/meteostick/).

## Supported Things

This binding is under development and currently only supports 1 thing: the RG-9 device. There is no support yet for the RG-11 or RG-15 and no plans to add these.

| Thing       | Type  | Description                                               |
|-------------|-------|-----------------------------------------------------------|
| hydreon_rg9 | Thing | Thing that contains the data channels for the RG-9 sensor |

## Binding Configuration

The Hydreon RG-9 thing needs to be added manually. There is no discovery.

## Thing Configuration

### hydreon_rg9 Configuration Options

| Option         | Description                                                                                                                        |
|----------------|------------------------------------------------------------------------------------------------------------------------------------|
| port           | Choose the serial port where the sensor is connected to                                                                            |
| intensityLevel | Intensity level that triggers the internal relay (J1 connector)                                                                    |
| holdTime       | Override the hold time of the internal relay. When empty, the hold time is dependant on the DIP switch setting (Monostable Extend) |
| disableLed     | Disable the LED of the device                                                                                                      |

## Channels

### Hydreon RG-9

| Channel Type ID        | Item Type          | Description                                                                                                        |
|------------------------|--------------------|--------------------------------------------------------------------------------------------------------------------|
| sensors#rain-intensity | Number             | Measured rain intensity, 0 for none up to 7 for violent.                                                           |
| sensors#temperature    | Number:Temperature | Temperature (according to documentation +/- 5°C). However, this item must be polled which is not implemented yet.  |
| interaction#kill       | Switch             | Send a KILL command (reset) to the sensor. Switch acts as a push-button and will turn to OFF again after 1 second. |
| device-info#reset      | String             | Last reset reason.                                                                                                 |
| device-info#power-days | Number             | Number of days since the last reset.                                                                               |
| device-info#dip-switch | String             | DIP switch setting on the device.                                                                                 |

## Full Example

### things/hydreon.things

Things can be defined in the .things file as follows:

```java
Thing hydreon:hydreon_rg9 [ port="/dev/ttySIM0", intensityLevel=1, holdTime=17, disableLed=true ]
```

In the above the port is required. The other parameters are optional and will default to the binding's presets.

