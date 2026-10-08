package com.shimmerresearch.simpleexamples;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;

/**
 * One device's calibrated values for {@link ShimmerBLECaptureExample}, one row per packet, columns
 * in the order the driver adds channels. Both drivers write the same format, so recordings from
 * the same device can be compared directly.
 */
final class CsvLog {
	private PrintWriter mOut;
	private File mFile;
	private List<String> mChannels;

	synchronized void open(File file) throws IOException {
		close();
		mOut = new PrintWriter(new BufferedWriter(new FileWriter(file)));
		mFile = file;
		mChannels = null;
	}

	synchronized void write(ObjectCluster ojc) {
		if (mOut == null) {
			return;
		}
		if (mChannels == null) {
			mChannels = new ArrayList<String>(ojc.getChannelNamesByInsertionOrder());
			mOut.println(String.join(",", mChannels));
		}
		StringBuilder row = new StringBuilder();
		for (int i = 0; i < mChannels.size(); i++) {
			if (i > 0) {
				row.append(',');
			}
			double value = ojc.getFormatClusterValue(mChannels.get(i), CHANNEL_TYPE.CAL.toString());
			if (!Double.isNaN(value)) {
				row.append(value);
			}
		}
		mOut.println(row);
	}

	/** Returns the file just closed, or null if none was open. */
	synchronized File close() {
		if (mOut == null) {
			return null;
		}
		mOut.close();
		mOut = null;
		File closed = mFile;
		mFile = null;
		return closed;
	}
}
