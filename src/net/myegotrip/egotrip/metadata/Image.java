package net.myegotrip.egotrip.metadata;




/**
 * Image Metadata
 * localContent should be the imageURI to the image in the gallery
 * 
 * @author gryphius
 *
 */
public class Image extends GenericMetadata {
	public Image(){
		setType(EgotripMetadata.IMAGE);
	}
	
	public Image(String imagePath){
		this();
		setImagePath(imagePath);
	}

	public String getImagePath(){
		return getLocalContent();
	}
	
	public void setImagePath(String path){
		setLocalContent(path);
	}
	
	@Override
	public String getServerContentAtInsertTime() {
		//server content is not available at insert time
		return null;
	}
}
