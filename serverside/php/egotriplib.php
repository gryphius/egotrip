<?php 

/**
 * 
 * Default Callback class, all methods return not implemented
 * @author gryphius
 *
 */
class EgoCallback{
	
	/**
	 * 
	 * called when a new location is added
	 * @param array $locationinfo
	 * @return the server generated ID for this new location
	 */
	function add_location($locationinfo){
		EgoLib::exit_notsupported("add_location add not implemented");
	}
	
	/**
	 * 
	 * location update. 
	 * @param array $locationinfo
	 */
	function update_location($locationinfo){
		EgoLib::exit_notsupported("update_location not implemented");
	}
	
	/**
	 * location delete
	 * @param array $locationinfo
	 */
	function delete_location($locationinfo){
		EgoLib::exit_notsupported("delete_location not implemented");
	}
	
	/**
	 * add metadata (image, icon, text...) to already uploaded location
	 * @param string $locationid
	 * @param string $type the type of metadata ("imagelink", "text", "icon" )
	 * @param string $content content (type of icon, id of the image, ...)
	 * @return the server generated ID for this new location
	 */
	function add_metadata($locationid,$type,$content){
		EgoLib::exit_notsupported("add_metadata not implemented");
	}
	
	/**
	 * remove metadata from a location
	 * @param id of the location $locationid
	 * @param metadataid to remove $metadataid. may be null in which case all metadata should be removed from that location
	 */
	function delete_metadata($locationid, $metadataid){
		EgoLib::exit_notsupported("delete_metadata not implemented");
	}
	
	/**
	 * 
	 * standard file upload. use PHP's move_uploaded_file to store it permanently!
	 * @param string $tmpfile - the temporary filename as returned ba $_FILES[...]["tmp_file"]
	 */
	function upload_file($tmpfile){
		EgoLib::exit_notsupported("upload_file not implemented");
	}
	
}


class EgoLib{
	
	var $callback;
	
	//known metadata types
	public static $METADATA_IMAGE="imagelink";
	public static $METADATA_TEXT="text";
	public static $METADATA_ICON="icon";
	
	/**
	 * command confirmation
	 * @param string $message
	 */
	public static function exit_ok($message=""){
		die("OK $message");	
	}
	
	/**
	 * tell the client to back off
	 */
	public static function exit_defer(){
		die("NOK DEFER Server overload, please try later");
	}
	
	/**
	 * 
	 * the requested operation is not supported
	 */
	public static function exit_notsupported($message=""){
		die("NOK NOTSUPPORTED $message");
	}
	
	/**
	 * 
	 * invalid request
	 * @param unknown_type $message
	 */
	public static function exit_invalid($message=""){
		die("NOK INVALID $message");
	}
	
	
	/**
	 * 
	 * Internal error
	 * @param unknown_type $message
	 */
	public static function exit_error($message=""){
		die("NOK ERROR $message");
	}
	
	/**
	 * syntax check for all known arguments
	 */
	public static function check_arguments(){
		foreach($_REQUEST as $name => $value){
			$name=strtolower($name);
			
			//TODO: add more checks
			
			switch($name){
				case "timestamp":
					if(!is_numeric($value))EgoLib::exit_invalid("timestamp must be numeric");
					break;
					
				case "tsorder":
					if(!is_numeric($value))EgoLib::exit_invalid("tsorder must be numeric");
					break;

				case "lat":
					if(!is_numeric($value))EgoLib::exit_invalid("latitude must be numeric");
					break;
				
				case "lng":
					if(!is_numeric($value))EgoLib::exit_invalid("longtitude must be numeric");
					break;

				case "bearing":
					if(!is_numeric($value))EgoLib::exit_invalid("bearing must be numeric");
					break;
					
				case "accuracy":
					if(!is_numeric($value))EgoLib::exit_invalid("accuracy must be numeric");
					break;
					
				case "speed":
					if(!is_numeric($value))EgoLib::exit_invalid("speed must be numeric");
					break;
					
				case "altitude":
					if(!is_numeric($value))EgoLib::exit_invalid("altitude must be numeric");
					break;
					
				case "tripname":
					if(empty($value))EgoLib::exit_invalid("tripname is empty");
					break;
					
				case "hidden":
					//TODO
					break;
				
				case "standalone":
					//TODO
					break;
			}
		}
	}
	
	/**
	 *  check if all required vars are there, exit invalid if one is missing
	 */
	public static function check_required($requiredvars){
		foreach($requiredvars as $req){
			if(!array_key_exists($req,$_REQUEST)){
				EgoLib::exit_invalid("missing $req");
			}
		}
	}
	
	
	/**
	 * get only relevant location info from $_REQUEST
	 */
	public static function extract_locationinfo(){
		$relevant=array("timestamp","tsorder","lat","lng","bearing","accuracy","speed","altitude","tripname","installationid","hidden","standalone","locationid");
		$arr=array();
		foreach($relevant as $info){
			if(array_key_exists($info,$_REQUEST)){
				$arr[$info]=$_REQUEST[$info];
			}
		}
		return $arr;
	}
	
	/**
	 * handle request and call callback
	 */
	function handle_request(){
		//get request
		if(empty($_REQUEST["cmd"])){
			EgoLib::exit_invalid("missing command attribute");
		}
		$cmd=strtolower($_REQUEST["cmd"]);
		
		//check if all arguments are syntactically ok
		EgoLib::check_arguments();
		
		//extract locationinfo
		$locationinfo=EgoLib::extract_locationinfo();
		
		//make sure we have a (dummy) callback
		if($this->callback == null){
			$this->callback=new EgoCallback();
		}
		
		switch($cmd){
			case "addlocation":
				$required=array("installationid","timestamp","lat","lng");
				EgoLib::check_required($required);
				$serverid=$this->callback->add_location($locationinfo);
				if(empty($serverid))$serverid="script_did_not_return_a_serverid";
				EgoLib::exit_ok($serverid);
				
				
			case "updatelocation":
				$required=array("installationid","locationid");
				EgoLib::check_required($required);
				$this->callback->update_location($locationinfo);
				if(empty($serverid))$serverid="";
				EgoLib::exit_ok($serverid);
				
			case "deletelocation":
				$required=array("installationid","locationid");
				EgoLib::check_required($required);
				$this->callback->delete_location($locationinfo);
				EgoLib::exit_ok("deleted");
				
				
			case "addmetadata":
				$required=array("installationid","mdatatype","locationid","mdatacontent");
				EgoLib::check_required($required);
				$serverid=$this->callback->add_metadata($locationinfo["locationid"],$_REQUEST["mdatatype"],$_REQUEST["mdatacontent"]);
				if(empty($serverid))$serverid="script_did_not_return_a_serverid";
				EgoLib::exit_ok($serverid);
				
			case "deletemetadata":
				$required=array("installationid","mdataid","locationid");
				EgoLib::check_required($required);
				$this->callback->delete_metadata($locationinfo["locationid"],$_REQUEST["mdataid"]);
				EgoLib::exit_ok("deleted");
				
			case "uploadfile":
				$required=array("installationid");
				EgoLib::check_required($required);
				if(!array_key_exists("uploadedfile",$_FILES)){
					EgoLib::exit_invalid("missing uploadedfile");
				} 
				$filename=$_FILES["uploadedfile"]["tmp_name"];
				$serverid=$this->callback->upload_file($filename);
				if(empty($serverid))$serverid="script_did_not_return_a_serverid";
				EgoLib::exit_ok($serverid);
			
			default:
				EgoLib::exit_notsupported("unknown command $cmd");
				
				
		}
		
		
		
	}
	
}


