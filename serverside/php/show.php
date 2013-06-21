<!DOCTYPE html>
<html>
  <head>
    <title>EgoTrip GPS Trail</title>
   
<?php
$uuid=$_REQUEST["uuid"];
if(empty($uuid))die("missing uuid");
?>
    <script type="text/javascript"
        src="https://maps.googleapis.com/maps/api/js?sensor=false"></script>

    <script type="text/javascript">
    var currentmap;
    var lc;

    function initialize() {
    	 var myOptions = {
    	          zoom: 8,
    	          center: new google.maps.LatLng(-34.397, 150.644),
    	          mapTypeId: google.maps.MapTypeId.ROADMAP
    	        };
        currentmap = new google.maps.Map(document.getElementById("map_current"),myOptions);
        google.maps.event.addDomListener(window, 'load', initialize);
        var latlngs = [];

<?php
$MOBILEPHONE=false;

if (stristr($_SERVER["HTTP_USER_AGENT"],"Android")){
 $MOBILEPHONE=true;
}

$MAPWIDTH=600;
$MAPHEIGHT=600;
$QUERYLIMIT=2000;
if($MOBILEPHONE){
 $MAPWIDTH=300;
 $MAPHEIGHT=300;
 $QUERYLIMIT=50;
}



$dbhost='localhost';
$dbuser='wgwh_mypagegps';
$dbpass='trackm3!';
$dbname='wgwh_mypagegps';

$db=mysql_connect($dbhost,$dbuser,$dbpass);
mysql_select_db($dbname);

$where= sprintf("uuid='%s' ",
mysql_real_escape_string($uuid));

$tripname=$_REQUEST["tripname"];
if(!empty($tripname)){
 $where.=sprintf(" AND tripname='%s' ",mysql_real_escape_string($tripname));
}

//admin override
if ($uuid=='stawsa'){
 $where='';
}

$result=mysql_query("SELECT * FROM (SELECT lat,lng,ts,accuracy,tripname from gpsdata  WHERE 1=1 AND $where order by ts DESC LIMIT $QUERYLIMIT) as tbl order by tbl.ts");
$ts=0;
$accuracy=0;
$pointarray=array();
$lastlat=null;
$lastlong=null;
while($row=mysql_fetch_array($result)){
	$pointarray[]=$row;
	$lat=$row["lat"];
	$lng=$row["lng"];
	$ts=$row["ts"];
	$ts=intval($ts/1000);
	$accuracy=$row["accuracy"];
	
  	//ignore duplicates
	if($lastlat==$lat && $lastlong==$lng){
		continue;
	}
	$lastlat=$lat;
	$lastlong=$lng;
	?>
	latlngs.push(new GLatLng(<?php echo $lat; ?>,<?php echo $lng; ?>));
	lc=new GLatLng(<?php echo $lat; ?>,<?php echo $lng; ?>);
	var m_lc=new GMarker(lc,{'title':'<?php echo strftime("%Y-%m-%d %H:%I",$row["ts"]/1000) ?>'});
	currentmap.addOverlay(m_lc);

	<?php 
}
?>
		var polyoptions = {};
		var poly = new GPolyline(latlngs, "#ff0000", 4, 0.5, polyoptions);
		currentmap.addOverlay(poly);
		currentmap.setCenter(lc, 11);
    }


    </script>
    

  </head>
  <body onload="initialize()" onunload="GUnload()">
<?php
if(!empty($accuracy)){
	echo "<i>accuracy: $accuracy m </i>";	
}
?>
	<!-- Google Public Location Badge -->
	<div id="map_current" style="width: <?php echo $MAPWIDTH; ?>px; height: <?php echo $MAPHEIGHT; ?>px"></div>
[ <a href="javascript:currentmap.setCenter(lc, 11);">Reset</a> ]

<h2>Recent Position Uploads</h2>
<table border="1">
<td>When</td>
<td>Trip</td>
<td>Position</td>
<?php if(!$MOBILEPHONE){
?>
	<td>Bearing</td>
	<td>Speed</td>
	<td>Accuracy</td>
	<td>&nbsp;</td>
<?php
}

//find duplicates
$buffpoint=null;
$buffstart=null;
$bufflat=null;
$bufflng=null;
$buffend=null;


function addRow(){

global $buffpoint,$buffstart,$buffend,$MOBILEPHONE;

	if($buffpoint==null){
		return;
	}

$starttime=strftime("%b %d %H:%I",$buffstart);

$timediff=$buffend-$buffstart;
$timestr="$starttime";
$minutes=intval($timediff/60);
if($timediff>0){
//buggy crap
//	$timestr="$starttime ($minutes min)";
}
if($minutes>60){
 $hours=intval($minutes/60);
// $timestr="$starttime ($hours h)";
}
?>
	<tr>
	<td><?= $timestr  ?></td>
	<td><?=$buffpoint["tripname"] ?></td>
	<td><?=round($buffpoint["lat"],4) ?>/<?=round($buffpoint["lng"],4) ?></td>
	<?php if(!$MOBILEPHONE){
?>
		<td><?=round($buffpoint["bearning"],4) ?></td>
		<td><?=round($buffpoint["speed"],4) ?></td>
		<td><?=round($buffpoint["accuracy"],4) ?></td>
		<td><a href="javascript:currentmap.setCenter(new GLatLng(<?php echo $buffpoint["lat"]; ?>,<?php echo $buffpoint["lng"]; ?>),14);">go there</a></td>
<?php
	}
?>
	</tr>
	<?
}


foreach($pointarray as $point){
	if($point["lat"]==$bufflat && $point["lng"] == $bufflng){
		$buffend=$point["ts"]/1000;
		continue;
	}

	addRow();

	$buffpoint=$point;
	$bufflat=$point["lat"];
	$bufflng=$point["lng"];
	$buffstart=$point["ts"]/1000;
	$buffend=$point["ts"]/1000;
}

addRow();
?>
</table>
</body>
 
</html>
