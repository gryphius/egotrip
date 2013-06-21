package net.myegotrip.egotrip.net;


public interface ProtocolConstants {
	//requests
	public static final String CMD_ADDLOCATION="addlocation";
	public static final String CMD_UPDATELOCATION="updatelocation";
	public static final String CMD_DELETELOCATION="deletelocation";
	
	public static final String CMD_ADDMETADATA="addmetadata";
	public static final String CMD_DELETEMETADATA="deletemetadata";
	
	public static final String CMD_UPLOADFILE="uploadfile";
	
	
	//protocol answers
	public static final String PROTO_OK="OK";
	public static final String PROTO_NOT_OK="NOK";
	public static final String PROTO_DEFER="DEFER";
	public static final String PROTO_NOTSUPPORTED="NOTSUPPORTED";
	public static final String PROTO_ERROR="ERROR";
	public static final String PROTO_INVALID="INVALID";
	
	
	//request states
	public static final int STATUS_UNKNOWN=-1;
	public static final int STATUS_OK=0;
	public static final int STATUS_DEFER=1;
	public static final int STATUS_NOTSUPPORTED=2;
	public static final int STATUS_INVALID=3;
	public static final int STATUS_ERROR=4;
	
	
}
