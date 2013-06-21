<?php

//initialize the helper lib
include_once("egotriplib.php");
$egolib=new EgoLib();

//create your handler class
class ExampleMysqlITripHandler extends EgoCallback{

	var $dbhost="localhost";
	var $dbuser="root";
	var $dbpass="";
	var $dbname="egotrip";
	var $filedir="uploads";
	var $mysqli;

	/**
	 * connect to the database, exit on error
	 */
	function init_mysql_connection(){
		$this->mysqli = new mysqli($this->dbhost, $this->dbuser,$this->dbpass, $this->dbname);
		if ($this->mysqli->connect_errno)
		EgoLib::exit_error("db connect failed: ". $mysqli->connect_error);
	}

	/**
	 *
	 * called when a new location is added
	 * @param array $locationinfo
	 * @return the server generated ID for this new location
	 */
	function add_location($locationinfo){
		$ip=$_SERVER["REMOTE_ADDR"];

		$installationid=$locationinfo["installationid"];
		$lat=$locationinfo["lat"];
		$lng=$locationinfo["lng"];
		$timestamp=$locationinfo["timestamp"];
		$tsorder=$locationinfo["tsorder"];
		$bearing=$locationinfo["bearing"];
		$speed=$locationinfo["speed"];
		$tripname=$locationinfo["tripname"];
		$altitude=$locationinfo["altitude"];
		$accuracy=$locationinfo["accuracy"];
		if(!empty($locationinfo["standalone"]) && $locationinfo["standalone"]){
			$standalone=1;
		} else {
			$standalone=0;
		}
		
		if(!empty($locationinfo["hidden"]) && $locationinfo["hidden"]){
			$hidden=1;
		} else {
			$hidden=0;
		}
		
		$insertquery="INSERT INTO gpsdata (installationid,ip,lat,lng,ts,tsorder,bearing,speed,tripname,altitude,accuracy,standalone,hidden)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
		if (!($stmt = $this->mysqli->prepare($insertquery)))
		EgoLib::exit_error("db prepare statement failed: ". $mysqli->error);

		if (!$stmt->bind_param("ssddiiddsddii", $installationid,$ip,$lat,$lng,$timestamp,$tsorder,$bearing,$speed,$tripname,$altitude,$accuracy, $standalone,$hidden))
		EgoLib::exit_error("db binding parameters failed:" . $stmt->error);

		if (!$stmt->execute())
		EgoLib::exit_error( "query execute failed:  " . $stmt->error);

		//important: return a unique id for the new location here!
		$insertid=$this->mysqli->insert_id;
		return $insertid;
	}

	/**
	 * called when a point should be updated. performs an individual update for each field, error exit
	 * if any update fails
	 * @param array $locationinfo
	 */
	function update_location($locationinfo){
		$updateableints=array("hidden","standalone","ts","tsorder");
		$updateabledecimals=array("lat","lng","bearing","speed","altitude","accuracy");
		$updateablestrings=array("tripname");
		$locationid=$locationinfo["locationid"];

		foreach($updateableints as $colname){
			if(!array_key_exists($colname,$locationinfo))continue;	
			$this->update_single_field($locationid,"i",$colname,$locationinfo[$colname]);
		}
		
		foreach($updateabledecimals as $colname){
			if(!array_key_exists($colname,$locationinfo))continue;	
			$this->update_single_field($locationid, "d",$colname,$locationinfo[$colname]);
		}
		
		foreach($updateablestrings as $colname){
			if(!array_key_exists($colname,$locationinfo))continue;	
			$this->update_single_field($locationid,"s",$colname,$locationinfo[$colname]);
		}
	}


	/**
	 *
	 * mysqli helper function to update a single column in the gpsdata table with prepared statements
	 * 
	 * @param char $type : mysqli datatype ("s" for strings, "i" for ints, "d" for decimals)
	 * @param string $colname : name of the column
	 * @param string $value : column value
	 */
	function update_single_field($id, $type,$colname,$value){
		$updatequery="UPDATE gpsdata SET modtime=now(), $colname = ? WHERE id= ?";
		if (!($stmt = $this->mysqli->prepare($updatequery)))
		EgoLib::exit_error("db prepare statement $updatequery failed: ". $mysqli->error);

		if (!$stmt->bind_param($type."i", $value,$id))
		EgoLib::exit_error("db binding parameters for statement $updatequery failed:" . $stmt->error);

		if (!$stmt->execute())
		EgoLib::exit_error( "query execute failed:  " . $stmt->error);
	}


	/**
	 * called when a location should be deleted from the database
	 * @param array $locationinfo
	 */
	function delete_location($locationinfo){
		$locationid=$locationinfo["locationid"];
		
		$deletequery="DELETE FROM gpsdata WHERE id= ?";
		if (!($stmt = $this->mysqli->prepare($deletequery)))
		EgoLib::exit_error("db delete prepare statement failed: ". $mysqli->error);

		if (!$stmt->bind_param("i", $locationid))
		EgoLib::exit_error("db binding parameters for delete failed:" . $stmt->error);

		if (!$stmt->execute())
		EgoLib::exit_error( "query execute failed:  " . $stmt->error);
	}
	
	
	function add_metadata($locationid,$type,$content){
		switch($type){
			case EgoLib::$METADATA_IMAGE:
				$this->update_single_field($locationid,"s","imagelink",$content);
				return $locationid."-imagelink";
				
			case EgoLib::$METADATA_ICON:
				$this->update_single_field($locationid,"s","icontype",$content);
				return $locationid."-icon";
				
			case EgoLib::$METADATA_TEXT:
				$this->update_single_field($locationid,"s","textmessage",$content);
				return $locationid."-txt";

			default:
				EgoLib::exit_notsupported("unknown metadatatype: $type");
		}
	
	}
	

	function delete_metadata($locationid, $metadataid){
		$sp=explode("-",$metadataid);
		if(count($sp)!=2){
			EgoLib::exit_invalid("invalid metadata id ");
		}
		$mlocid=$sp[0];
		$mtype=$sp[1];

		//locationid and metadatait should be the same
		if($mlocid!=$locationid){
			EgoLib::exit_invalid("invalid location/metadata id $mlocid / $locationid");
		}
		
		switch($mtype){
			case "imagelink":
				$this->update_single_field($locationid,"s","imagelink",null);
				break;
				
			case "icon":
				$this->update_single_field($locationid,"s","icontype",null);
				break;
				
			case "txt":
				$this->update_single_field($locationid,"s","textmessage",null);
				break;
		}
	}
	
	function upload_file($tmpfile){
		//create a hash of the file
		$filehash=sha1_file($tmpfile);
		
		//we assume the file is a jpg
		$targetfile=$this->filedir."/".$filehash.".jpg";
		
		$success=move_uploaded_file($tmpfile,$targetfile);
		if(!$success){
			EgoLib::exit_error("could not save file");
		}
		return $filehash;
	}

}


//register your handler
$myhandler=new ExampleMysqlITripHandler();
$egolib->callback=$myhandler;

//init mysql connection
$myhandler->dbname="gpsdata";
$myhandler->init_mysql_connection();

//tell the lib to parse the request and call your handler accordingly
$egolib->handle_request();
