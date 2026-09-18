package com.shimmerresearch.driver;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.shimmerresearch.algorithms.API_00002_Filters;
import com.shimmerresearch.driverUtilities.API_00009_TimestampUnwrapTest;
import com.shimmerresearch.driverUtilities.API_00010_TimestampUnwrapVectorsTest;
import com.shimmerresearch.verisense.API_00004_VerisenseConfigByteParsingAndGeneration;
import com.shimmerresearch.verisense.communication.API_00003_VerisenseProtocolByteCommunicationTest;

@RunWith(Suite.class)
@SuiteClasses({ 
	API_00002_Filters.class,
	API_00003_VerisenseProtocolByteCommunicationTest.class,
	API_00004_VerisenseConfigByteParsingAndGeneration.class,
	API_00009_TimestampUnwrapTest.class,
	API_00010_TimestampUnwrapVectorsTest.class
})

public class API_00005_Suite_ShimmerDriver {

}
