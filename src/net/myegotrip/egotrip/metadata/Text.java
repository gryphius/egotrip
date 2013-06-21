package net.myegotrip.egotrip.metadata;


public class Text extends GenericMetadata {
	public Text(){
		setType(EgotripMetadata.TEXT);
	}
	
	public Text(String text){
		this();
		setText(text);
	}
	
	public void setText(String text){
		setLocalContent(text);
	}
	
	public String getText(){
		return getLocalContent();
	}
	
	@Override
	public String getServerContentAtInsertTime() {
		return getLocalContent();
	}
	
}
