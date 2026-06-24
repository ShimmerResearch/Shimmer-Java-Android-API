package com.shimmerresearch.verisense.payloaddesign;

public class AsmBinaryFileConstants {
	
	public static final boolean ONLY_ALLOW_FULL_32KB_PAYLOADS = false;
	public static final boolean OPTIMISE_32KB_PAYLOAD_SIZE = true;

	public static final double TICKS_PER_SECOND = 32768.0;
	public static final double TICKS_PER_MILLISECOND = TICKS_PER_SECOND/1000;
	public static final double TICKS_PER_MINUTE = TICKS_PER_SECOND*60;

	public static final boolean IS_SPI_BUS_ADDING_HEADER_BYTE = true;
	public static final int ACCEL_SPI_BUS_HEADER_BYTES = 1;
	
	public class PAYLOAD_CONFIG_BYTE_INDEX {
		public static final int PAYLOAD_CONFIG0 = 0;
		public static final int PAYLOAD_CONFIG1 = 1;
		public static final int REV_FW_MAJOR = 2;
		public static final int REV_FW_MINOR = 3;
		public static final int REV_FW_INTERNAL_LSB = 4;
		public static final int REV_FW_INTERNAL_MSB = 5;
		
		public static final int RESET_REASON = 6;
		public static final int RESET_COUNTER_LSB = 7;
		public static final int RESET_COUNTER_MSB = 8;
		public static final int FIRST_PAYLOAD_AFTER_RESET_LSB = 9;
		public static final int FIRST_PAYLOAD_AFTER_RESET_MSB = 10;
		public static final int REV_HW_MAJOR = 11;
		public static final int REV_HW_MINOR = 12;
		public static final int PAYLOAD_CONFIG2 = 13;
		public static final int PAYLOAD_CONFIG3 = 14;
		public static final int PAYLOAD_CONFIG4 = 15;
		public static final int PAYLOAD_CONFIG5 = 16;
		public static final int PAYLOAD_CONFIG6 = 17;
		public static final int PAYLOAD_CONFIG7 = 18;
		public static final int PAYLOAD_CONFIG8 = 19;
		public static final int PAYLOAD_CONFIG9 = 20;
		public static final int PAYLOAD_CONFIG10 = 21;
		public static final int PAYLOAD_CONFIG11 = 22;
		public static final int PAYLOAD_CONFIG12 = 23;
		public static final int PAYLOAD_CONFIG13 = 24;
		// Second-generation (payload design v13) only: GEN_CFG_3 at abs byte 29 (rel 25)
		// carries the mag/light/skin-temp/algo-hub enables + LED mode.
		public static final int GEN_CFG_3 = 25;
	}
	
	public class BYTE_COUNT {
		// Separation here between "Payload Contents" and "Payload" as the Payload
		// Contents alone can be compressed in the future but the payload as a whole can not
		public static final int PAYLOAD_CONTENTS_RESERVED_SIZE = 32768;

		public static final int PAYLOAD_CONTENTS_RTC_BYTES_MINUTES = 4;
		public static final int PAYLOAD_CONTENTS_RTC_BYTES_TICKS = 3;
		public static final int PAYLOAD_CONTENTS_RTC_BYTES = BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_MINUTES + BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_TICKS;
		public static final int PAYLOAD_CONTENTS_TEMPERATURE = 2;
		public static final int PAYLOAD_CONTENTS_BATTERY_VOLTAGE = 2;
		public static final int PAYLOAD_CONTENTS_FOOTER_GEN1_TO_GEN7_AND_GEN9 = BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES + BYTE_COUNT.PAYLOAD_CONTENTS_TEMPERATURE + BYTE_COUNT.PAYLOAD_CONTENTS_BATTERY_VOLTAGE;
		public static final int PAYLOAD_CONTENTS_FOOTER_GEN8_ONLY = BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_MINUTES + BYTE_COUNT.PAYLOAD_CONTENTS_TEMPERATURE + BYTE_COUNT.PAYLOAD_CONTENTS_BATTERY_VOLTAGE;
		public static final int PAYLOAD_CONTENTS_FOOTER_GEN10_OR_ABOVE = (BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES*2) + BYTE_COUNT.PAYLOAD_CONTENTS_TEMPERATURE + BYTE_COUNT.PAYLOAD_CONTENTS_BATTERY_VOLTAGE;
		public static final int PAYLOAD_CONTENTS_GEN8_SENSOR_ID = 1;

		public static final int PAYLOAD_INDEX = 2;
		public static final int PAYLOAD_LENGTH = 2;
		public static final int PAYLOAD_CONFIG_CORE = 2;
		public static final int PAYLOAD_CRC = 2;
		public static final int PAYLOAD_FOOTER = PAYLOAD_CRC;
	}

	public enum DATA_COMPRESSION_MODE {
		NONE,
		ZLIB,
		XZ;
	}

}
