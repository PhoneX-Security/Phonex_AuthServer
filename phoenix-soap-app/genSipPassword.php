<?php
$sip = $argv[1];
$password = $argv[2];
$ha1  = getHA1_1($sip, $password);
$ha1b = getHA1_2($sip, "phone-x.net", $password);
printf("User name: [%s], pwd: [%s], ha1: [%s], ha1b: [%s]\n", $sip, $password, $ha1, $ha1b);

function getHA1_1($sip, $password) {
	// split sip by @
	$arr = explode("@", $sip, 2);
	if ($arr==null || count($arr)!=2) {
		var_dump($arr);
		throw new Exception("Invalid SIP format");
	}	
	return getHA1_2($arr[0], $arr[1], $password);
}
	
function getHA1_2($username, $domain, $password) {
	$x = $username . ":" . $domain . ":" . $password;
	return md5($x);	
}

