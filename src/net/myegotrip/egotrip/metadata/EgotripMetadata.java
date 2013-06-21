package net.myegotrip.egotrip.metadata;


public interface EgotripMetadata {
	//known metadata types
	public static String IMAGE="imagelink";
	public static String ICON="icon";
	public static String TEXT="text";
	
	/**
	 * 
	 * @return Local Database ID of the location this metadata is attached to. 
	 * 
	 */
	public int getLocalLocationID();
	
	/**
	 * 
	 * @return metadata type
	 */
	public String getType();
	
	/**
	 * local content representation
	 * @return
	 */
	public String getLocalContent();
	

	/**
	 * the content that should be transferred to the server
	 * may be null at insert time to indicate this will be filled later
	 * (for example for images, which don't have the upload id ready ad insert time)
	 * return getLocalContent() for inline transfers
	 * @return
	 */
	public String getServerContentAtInsertTime();
	
	public String getCurrentServerContent();
	
	public void setLocalID(int id);
	public int getLocalID();	
	public String getServerID();
	public void setServerID(String newServerid);
	public boolean isDeleted();
	
}
