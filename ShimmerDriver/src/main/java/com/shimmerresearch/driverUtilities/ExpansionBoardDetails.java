package com.shimmerresearch.driverUtilities;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID_SR_CODES;

/**
 * Holds the Shimmer's Expansion board information as read from the memory chip
 * on the Shimmers expansion board.
 * 
 * @author Mark Nolan
 *
 */
public class ExpansionBoardDetails implements Serializable {
	
	/** * */
	private static final long serialVersionUID = 1104467341565122266L;
	
	/** Stores the SR number for a specific Shimmer version */
	public int mExpansionBoardId = HW_ID_SR_CODES.UNKNOWN;
	/** Stores the revision of the board */
	public int mExpansionBoardRev = HW_ID_SR_CODES.UNKNOWN;
	/** Stores the special revision number - for custom modifications to standard Shimmers */
	public int mExpansionBoardRevSpecial = HW_ID_SR_CODES.UNKNOWN;
	
	public byte[] mExpBoardArray = new byte[]{};
	
	public static final List<Integer> LIST_OF_UNITS_WITH_EXP_BRDS = Arrays.asList(
			HW_ID.SHIMMER_3,
			HW_ID.SHIMMER_GQ_802154_LR,
			HW_ID.SHIMMER_GQ_802154_NR,
			HW_ID.SHIMMER_ECG_MD,
			HW_ID.SHIMMER_4_SDK
			);
	
	public ExpansionBoardDetails(
			int boardID,
			int boardRev,
			int specialRev) {
		parseExpansionBoardDetails(boardID, boardRev, specialRev);
	}
	
	public ExpansionBoardDetails(byte[] expBoardArray) {
		parseExpansionBoardDetails(expBoardArray);
	}
	
	public ExpansionBoardDetails() {
		// TODO Auto-generated constructor stub
	}

	public void parseExpansionBoardDetails(byte[] expBoardArray) {
		if(expBoardArray!=null){
			this.mExpBoardArray = expBoardArray; 
			int boardID = mExpBoardArray[0] & 0xFF;
			int boardRev = mExpBoardArray[1] & 0xFF;
			int specialRev = mExpBoardArray[2] & 0xFF;
			parseExpansionBoardDetails(boardID, boardRev, specialRev);
		}
	}

	public void parseExpansionBoardDetails(int boardID, int boardRev, int specialRev) {
		mExpansionBoardId = boardID;
		mExpansionBoardRev = boardRev;
		mExpansionBoardRevSpecial = specialRev;
	}

	public String getExpansionBoardParsed() {
		String boardName = UtilShimmer.STRING_CONSTANT_FOR_UNKNOWN;
		
//		if(mExpansionBoardId== || mExpansionBoardId==HW_ID_SR_CODES.UNKNOWN){
//			boardName = ShimmerVerDetails.EXP_BRD_NONE;
//		}
//		else {
			if(ShimmerVerDetails.mMapOfShimmerHardware.containsKey(mExpansionBoardId)){
				boardName = ShimmerVerDetails.mMapOfShimmerHardware.get(mExpansionBoardId);
			}
			else {
				boardName = getBoardVerString();
			}
//		}
		return boardName;
	}

	public String getExpansionBoardParsedWithVer() {
		String boardNameWithVer = UtilShimmer.STRING_CONSTANT_FOR_UNKNOWN;
		String boardVerString = getBoardVerString();
		
		boardNameWithVer = getExpansionBoardParsed();
		if(!boardNameWithVer.equals(boardVerString)){
			boardNameWithVer += " (" + boardVerString + ")";
		}
		return boardNameWithVer;
	}
	
	public String getBoardVerString(){
		return ("SR" + mExpansionBoardId + "." + mExpansionBoardRev + "." + mExpansionBoardRevSpecial);
	}
	
	public boolean isExpansionBoardValid(){
		if(!(mExpansionBoardId==0 && mExpansionBoardRev==0 && mExpansionBoardRevSpecial==0)
				&& (mExpansionBoardId!=HW_ID_SR_CODES.UNKNOWN && mExpansionBoardRev!=HW_ID_SR_CODES.UNKNOWN && mExpansionBoardRevSpecial!=HW_ID_SR_CODES.UNKNOWN)
				&& (mExpansionBoardId!=HW_ID_SR_CODES.LOG_FILE && mExpansionBoardRev!=HW_ID_SR_CODES.LOG_FILE && mExpansionBoardRevSpecial!=HW_ID_SR_CODES.LOG_FILE)){
			return true;
		}
		return false;
	}

	public HashMap<Integer, String> getMapOfByteDescriptions(){
		HashMap<Integer, String> mapOfByteDescriptions = new HashMap<Integer, String>();
		
		mapOfByteDescriptions.put(0, "SR number");
		mapOfByteDescriptions.put(1, "Revision");
		mapOfByteDescriptions.put(2, "Special Revision");
		
		if(mExpansionBoardId==HW_ID_SR_CODES.SHIMMER_GQ_802154_NR || mExpansionBoardId==HW_ID_SR_CODES.SHIMMER_GQ_802154_LR){
			mapOfByteDescriptions.put(5, "MAC_ID_1");
			mapOfByteDescriptions.put(6, "MAC_ID_2");
			mapOfByteDescriptions.put(7, "MAC_ID_3");
			mapOfByteDescriptions.put(8, "MAC_ID_4");
			mapOfByteDescriptions.put(9, "MAC_ID_5");
			mapOfByteDescriptions.put(10, "MAC_ID_6");
		}
		
		return mapOfByteDescriptions;
	}
	
	public static byte[] generateExpBrdIdArrayEmpty() {
		byte[] byteArray = new byte[16];
		
		//Clear byte array
		for(int i=0;i<byteArray.length;i++){
			byteArray[i] = (byte) (0xFF);
		}
		return byteArray;
	}
	
	public static byte[] generateExpBrdIdArray(int srNumber, int verMajor, int verMinor) {
		byte[] byteArray = generateExpBrdIdArrayEmpty();

		byteArray[0] = (byte) srNumber;
		byteArray[1] = (byte) verMajor;
		byteArray[2] = (byte) verMinor;
		
		//reserve two bytes for future use
		byteArray[3] = (byte) 0xFF;
		byteArray[4] = (byte) 0xFF; 
		
		return byteArray;
	}

	public int getExpansionBoardId() {
		return mExpansionBoardId;
	}

	public int getExpansionBoardRev() {
		return mExpansionBoardRev;
	}

	public int getExpansionBoardRevSpecial() {
		return mExpansionBoardRevSpecial;
	}

	/**
	 * Lexicographic "&gt;=" comparison over (revision, special revision) for a
	 * given SR number. The revision is the primary key: e.g. board SR48.8.0 is
	 * considered &gt;= threshold SR48.7.2 because the revision (8) already exceeds
	 * the threshold revision (7), regardless of the special-rev values.
	 * <p>
	 * NB: a naive {@code rev&gt;=minRev && special&gt;=minSpecial} check is WRONG
	 * (it would reject SR48.8.0 against threshold SR48.7.2) - keep this
	 * lexicographic.
	 *
	 * @param srNumber   the SR number (expansion-board id) to match
	 * @param minRev     minimum board revision
	 * @param minSpecial minimum special revision (only compared when rev is equal)
	 * @return true if this board's SR number matches and (rev, special) &gt;= (minRev, minSpecial)
	 */
	public boolean isSrNumberGte(int srNumber, int minRev, int minSpecial) {
		if (mExpansionBoardId != srNumber) {
			return false;
		}
		if (mExpansionBoardRev != minRev) {
			return mExpansionBoardRev > minRev;
		}
		return mExpansionBoardRevSpecial >= minSpecial;
	}

}
