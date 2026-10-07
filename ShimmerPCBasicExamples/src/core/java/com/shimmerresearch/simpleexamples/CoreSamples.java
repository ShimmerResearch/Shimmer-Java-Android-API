package com.shimmerresearch.simpleexamples;

import java.util.List;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.logandstream.Channel;

/**
 * The protocol core's samples as the Java driver's ObjectClusters, for code written against those
 * (plots, the CSV log). The core's channels already are the driver's: the same names, units and
 * order, each calibrated (CAL) and raw (UNCAL).
 */
final class CoreSamples {

	private static final String SYSTEM_TIMESTAMP = "System_Timestamp";

	private CoreSamples() {
	}

	static String format(Channel channel) {
		return channel.format == Channel.Format.CALIBRATED ? CHANNEL_TYPE.CAL.toString() : CHANNEL_TYPE.UNCAL.toString();
	}

	/** As buildMsg fills one: the device's name, the raw packet, the PC time, then every channel. */
	static ObjectCluster toObjectCluster(List<Channel> channels, double[] values, byte[] packet, String deviceName) {
		ObjectCluster oc = new ObjectCluster();
		oc.setShimmerName(deviceName);
		oc.mRawData = packet;
		for (int i = 0; i < channels.size() && i < values.length; i++) {
			Channel c = channels.get(i);
			oc.addDataToMap(c.name, format(c), c.units, values[i]);
			if (c.name.equals(SYSTEM_TIMESTAMP) && c.format == Channel.Format.CALIBRATED) {
				oc.setSystemTimeStamp(values[i]);
			}
		}
		return oc;
	}
}
