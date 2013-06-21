package net.myegotrip.egotrip.net;

public class ServerReply implements ProtocolConstants {
	private int status = STATUS_UNKNOWN;
	private String errorMessage;
	private String argument;
	private String fullAnswer;
	private long timestamp;
	
	public ServerReply(){
		timestamp=System.currentTimeMillis();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof ServerReply){
			ServerReply other=(ServerReply)o;
			return (other.getTimestamp()==timestamp && other.getStatus() == status);
		}
		return false;
	}
	
	public String getFullAnswer() {
		return fullAnswer;
	}

	public void setFullAnswer(String fullAnswer) {
		this.fullAnswer = fullAnswer;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getStatus() {
		return status;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setArgument(String argument) {
		this.argument = argument;
	}

	public String getArgument() {
		return argument;
	}

	public long getTimestamp() {
		return timestamp;
	}

}
