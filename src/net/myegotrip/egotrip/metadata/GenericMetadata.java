package net.myegotrip.egotrip.metadata;


public class GenericMetadata implements EgotripMetadata {
	private String type;
	private String localContent;
	private int localID;
	private String serverID;
	private int localLocationID;
	private boolean deleted;
	private String serverContent;
	
	@Override
	public int getLocalLocationID() {
		return localLocationID;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public String getLocalContent() {
		return localContent;
	}

	@Override
	public int getLocalID() {
		return localID;
	}

	public void setLocalID(int localID) {
		this.localID = localID;
	}

	@Override
	public String getServerID() {
		return serverID;
	}

	public void setServerID(String serverID) {
		this.serverID = serverID;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setLocalContent(String localcontent) {
		this.localContent = localcontent;
	}

	public void setLocalLocationID(int localLocationID) {
		this.localLocationID = localLocationID;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	@Override
	public boolean isDeleted() {
		return deleted;
	}

	@Override
	/**
	 * server content at insert time for subclasses!
	 */
	public String getServerContentAtInsertTime() {
		return serverContent;
	}
	
	@Override
	/**
	 * server content at insert time for subclasses!
	 */
	public String getCurrentServerContent() {
		return serverContent;
	}
	
	public void setServerContent(String content){
		serverContent=content;
	}
	
	@Override
	public String toString() {
		String sContent=getServerContentAtInsertTime();
		if(sContent!=null && sContent.length()>100){
			sContent=sContent.substring(0, 99);
		}
		return "GenericMetadata type="+getType()+" Lcontent="+getLocalContent()+" ServerContent="+sContent+" LID/SID="+getLocalID()+"/"+getServerID();
		
	}

}
