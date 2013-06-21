package net.myegotrip.egotrip.metadata;


public class Icon extends GenericMetadata {
	public Icon(){
		setType(EgotripMetadata.ICON);
	}
	
	public Icon(String iconType){
		this();
		setIconType(iconType);
	}
	
	@Override
	public String getServerContentAtInsertTime() {
		//icon type transferred inline
		return getLocalContent();
	}
	
	public void setIconType(String icontype){
		setLocalContent(icontype);
	}
	
	public String getIconType(String ictontype){
		return getLocalContent();
	}
	
}
