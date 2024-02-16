package com.probase.reservationticketingwebservice.authenticator;


import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.security.auth.login.LoginException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.probase.reservationticketingwebservice.enumerations.CardStatus;
import com.probase.reservationticketingwebservice.enumerations.CardType;
import com.probase.reservationticketingwebservice.enumerations.DeviceStatus;
import com.probase.reservationticketingwebservice.enumerations.DeviceType;
import com.probase.reservationticketingwebservice.enumerations.RegionType;
import com.probase.reservationticketingwebservice.enumerations.RequestType;
import com.probase.reservationticketingwebservice.enumerations.RoleType;
import com.probase.reservationticketingwebservice.enumerations.TransactionStatus;
import com.probase.reservationticketingwebservice.enumerations.UserStatus;
import com.probase.reservationticketingwebservice.models.AuditTrail;
import com.probase.reservationticketingwebservice.models.CardScheme;
import com.probase.reservationticketingwebservice.models.Client;
import com.probase.reservationticketingwebservice.models.District;
import com.probase.reservationticketingwebservice.models.Station;
import com.probase.reservationticketingwebservice.models.TripCard;
import com.probase.reservationticketingwebservice.models.Transaction;
import com.probase.reservationticketingwebservice.models.User;
import com.probase.reservationticketingwebservice.util.Application;
import com.probase.reservationticketingwebservice.util.ERROR;
import com.probase.reservationticketingwebservice.util.PrbCustomService;
import com.probase.reservationticketingwebservice.util.ServiceLocator;
import com.probase.reservationticketingwebservice.util.SwpService;
import com.probase.reservationticketingwebservice.util.UtilityHelper;
import com.sun.org.apache.bcel.internal.generic.NEW;

public final class StationFunction {

    private static StationFunction authenticator = null;

    // A user storage which stores <username, password>
    private final Map<String, String> usersStorage = new HashMap();

    // A service key storage which stores <service_key, username>
    private final Map<String, String> serviceKeysStorage = new HashMap();

    // An authentication token storage which stores <service_key, auth_token>.
    private final Map<String, String> authorizationTokensStorage = new HashMap();
    
    private static Logger log = Logger.getLogger(StationFunction.class);
	private ServiceLocator serviceLocator = null;
	public SwpService swpService = null;
	public PrbCustomService swpCustomService = PrbCustomService.getInstance();
	Application application = null;
	SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private StationFunction() {
        // The usersStorage pretty much represents a user table in the database
        //usersStorage.put( "username1", "passwordForUser1" );
        //usersStorage.put( "username2", "passwordForUser2" );
        //usersStorage.put( "username3", "passwordForUser3" );

        /**
         * Service keys are pre-generated by the system and is given to the
         * authorized client who wants to have access to the REST API. Here,
         * only username1 and username2 is given the REST service access with
         * their respective service keys.
         */
        //serviceKeysStorage.put( "f80ebc87-ad5c-4b29-9366-5359768df5a1", "username1" );
        //serviceKeysStorage.put( "3b91cab8-926f-49b6-ba00-920bcf934c2a", "username2" );
    	serviceLocator = ServiceLocator.getInstance();
    }

    public static StationFunction getInstance() {
        if ( authenticator == null ) {
            authenticator = new StationFunction();
        }

        return authenticator;
    }
    
    
	public Response createNewStation(String token, String stationName,
			Long districtId, String city, String stationCode, Boolean isMainStation, String requestId, String ipAddress, 
			String clientCode, Double targetPercentage, String regionType) {
		// TODO Auto-generated method stub
		JsonObjectBuilder jsonObject = Json.createObjectBuilder();
    	
		try{
			
			if(stationName==null || districtId==null || city==null || clientCode==null)
			{
				jsonObject.add("status", ERROR.INCOMPLETE_PARAMETERS);
				jsonObject.add("message", "Incomplete Parameters provided in request.");
				JsonObject jsonObj = jsonObject.build();
	            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			}
			
			if(regionType!=null)
			{
				try
				{
					RegionType.valueOf(regionType);
				}
				catch(IllegalArgumentException e)
				{
					jsonObject.add("status", ERROR.INCOMPLETE_PARAMETERS);
					jsonObject.add("message", "Region type provided is invalid");
					JsonObject jsonObj = jsonObject.build();
		            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
				}
			}
			
			
			swpService = serviceLocator.getSwpService();
			Application app = Application.getInstance(swpService);
			JSONObject verifyJ = UtilityHelper.verifyToken(token, app);
			if(verifyJ.length()==0 || (verifyJ.length()>0 && verifyJ.has("active") && verifyJ.getInt("active")==0))
			{
				jsonObject.add("status", ERROR.FORCE_LOGOUT_USER);
				jsonObject.add("message", "Your session has expired. Please log in again");
				JsonObject jsonObj = jsonObject.build();
	            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			}
			
			String username = verifyJ.has("username") ? verifyJ.getString("username") : null;
			//log.inforequestId + "username ==" + (username==null ? "" : username));
			String roleCode_ = verifyJ.has("roleCode") ? verifyJ.getString("roleCode") : null;
			//log.inforequestId + "roleCode_ ==" + (roleCode_==null ? "" : roleCode_));
			User user = null;
			RoleType roleCode = null;
			
			if(roleCode_ != null)
			{
				roleCode = RoleType.valueOf(roleCode_);
			}
			
			if(username!=null)
			{
				user = (User)this.swpService.getUniqueRecordByHQL("select tp from User tp where tp.username = '" + username + "' AND " +
						"tp.userStatus = " + UserStatus.ACTIVE.ordinal() + " AND tp.deletedAt IS NULL AND tp.client.clientCode = '"+clientCode+"'");
				//roleCode = user.getRoleCode();
			}
			
			District district = null;
			if(districtId!=null)
			{
				//log.info"Select tp from District tp where tp.districtId = "+districtId + " AND tp.deletedAt IS NULL");
				district = (District)this.swpService.getUniqueRecordByHQL("Select tp from District tp where tp.districtId = "+districtId + " AND tp.deletedAt IS NULL");
				if(district==null)
				{
					jsonObject.add("status", ERROR.INCOMPLETE_PARAMETERS);
					jsonObject.add("message", "The districtId is invalid");
					JsonObject jsonObj = jsonObject.build();
		            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
				}
			}
			
			Client client = null;
			if(clientCode!=null)
			{
				client = (Client)this.swpService.getUniqueRecordByHQL("Select tp from Client tp where tp.clientCode = '"+ clientCode + "' AND tp.deletedAt IS NULL");
				if(client==null)
				{
					jsonObject.add("status", ERROR.INCOMPLETE_PARAMETERS);
					jsonObject.add("message", "The clientCode is invalid");
					JsonObject jsonObj = jsonObject.build();
		            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
				}
			}
			
			
			
			if(roleCode==null || (roleCode!=null && !roleCode.equals(RoleType.ADMIN_STAFF)))
			{
				jsonObject.add("status", ERROR.INVALID_STATION_CREATION_PRIVILEDGES);
				jsonObject.add("message", "Invalid Station Creation Priviledges");
				JsonObject jsonObj = jsonObject.build();
	            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			}
			
				
			
			JSONArray jsonArray = new JSONArray();
			JSONObject txnObjects = new JSONObject();
			int a = 0;
			String serialNo = "";
			
			Station station = new Station();
			//log.info"stationCode === " + stationCode);
			if(stationCode!=null)
			{
				String hql = "Select tp from Station tp where tp.stationCode = '"+stationCode+"' AND tp.client.clientCode = '"+clientCode+"' " +
						"AND tp.deletedAt IS NULL";
				//log.info"hql edit === " + hql);
				station = (Station)swpService.getUniqueRecordByHQL(hql);
				if(station==null)
				{
					jsonObject.add("status", ERROR.STATION_NOT_FOUND);
					jsonObject.add("message", "Station matching station code provided can not be found");
					JsonObject jsonObj = jsonObject.build();
		            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
				}
				station.setUpdatedAt(new Date());
				station.setClient(client);
				station.setStationName(stationName);
				station.setCity(city);
				station.setMainStation(isMainStation!=null && isMainStation.equals(Boolean.TRUE) ? Boolean.TRUE : Boolean.FALSE);
				station.setDistrict(district);
				if(regionType!=null)
					station.setRegionType(RegionType.valueOf(regionType));
				
				station.setTargetPercentage(targetPercentage);
				this.swpService.updateRecord(station);
				
				
				AuditTrail ad = UtilityHelper.createAuditTrailEntry(ipAddress, RequestType.UPDATE_STATION, requestId, this.swpService, 
						verifyJ.has("username") ? verifyJ.getString("username") : null, station.getStationId(), Station.class.getName(), 
						"Update Station. Station name: " + station.getStationName() + " | Updated By " + user.getFirstName() + " " + user.getLastName(), clientCode);
				jsonObject.add("message", "Station(s) updated successfully");
			}
			else
			{

				//log.info"station code is null");
				station.setCreatedAt(new Date());
				station.setUpdatedAt(new Date());
				station.setStationCode(RandomStringUtils.random(5, false, true).toUpperCase());
				station.setClient(client);
				station.setStationName(stationName);
				station.setCity(city);
				station.setStatus(Boolean.TRUE);
				station.setMainStation(isMainStation!=null && isMainStation.equals(Boolean.TRUE) ? Boolean.TRUE : Boolean.FALSE);
				station.setDistrict(district);
				station = (Station)this.swpService.createNewRecord(station);
				
				
				AuditTrail ad = UtilityHelper.createAuditTrailEntry(ipAddress, RequestType.NEW_STATION_CREATION, requestId, this.swpService, 
						verifyJ.has("username") ? verifyJ.getString("username") : null, station.getStationId(), Station.class.getName(), 
						"New Station. Station name: " + station.getStationName() + " | Created By " + user.getFirstName() + " " + user.getLastName(), clientCode);
				jsonObject.add("message", "Station(s) generated successfully");
			}
			
			
			
			
			jsonObject.add("status", ERROR.GENERAL_SUCCESS);
			jsonObject.add("stationList", new Gson().toJson(station));
			JsonObject jsonObj = jsonObject.build();
            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			
		}catch(Exception e)
		{
			log.warn(e); log.error("exception...", e);
			e.printStackTrace();
			JsonObject jsonObj = jsonObject.build();
            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
		}
	}


	public Response listStations(String token, String requestId, String clientCode) {
		// TODO Auto-generated method stub
		JsonObjectBuilder jsonObject = Json.createObjectBuilder();
    	
    	
		try{
			
			
			swpService = serviceLocator.getSwpService();
			Application app = Application.getInstance(swpService);
			JSONObject verifyJ = UtilityHelper.verifyToken(token, app);
			if(verifyJ.length()==0 || (verifyJ.length()>0 && verifyJ.has("active") && verifyJ.getInt("active")==0))
			{
				jsonObject.add("status", ERROR.FORCE_LOGOUT_USER);
				jsonObject.add("message", "Your session has expired. Please log in again");
				JsonObject jsonObj = jsonObject.build();
	            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			}
			
			
			
			
			String hql = "Select tp from Station tp where tp.deletedAt IS NULL";
			String sql = "";
			hql = hql + sql;
			Collection<Station> stations= (Collection<Station>)this.swpService.getAllRecordsByHQL(hql);
			Iterator<Station> stationIterator = stations.iterator();
			
			String hql1 = "Select count(tp.id) as idCount from Station tp WHERE tp.deletedAt IS NULL";
			//log.info"4.hql ==" + hql1);
			List<Long> totalStationCount = (List<Long>)swpService.getAllRecordsByHQL(hql1);
			Long totalStations = totalStationCount!=null ? totalStationCount.iterator().next() : 0;
			//log.info"totalStationCount ==" + totalStations);
			
			
			jsonObject.add("message", "Stations listed successfully");
			jsonObject.add("status", ERROR.GENERAL_SUCCESS);
			jsonObject.add("stationList", new Gson().toJson(stations));
			jsonObject.add("totalStationCount", totalStations);
			JsonObject jsonObj = jsonObject.build();
            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			
		}catch(Exception e)
		{
			log.warn(e); log.error("exception...", e);
			e.printStackTrace();
			JsonObject jsonObj = jsonObject.build();
            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
		}
	}

	public Response getStation(String stationCode, String token, String requestId, String clientCode) {
		// TODO Auto-generated method stub
		JsonObjectBuilder jsonObject = Json.createObjectBuilder();
    	
    	
		try{
			
			if((stationCode==null) || clientCode==null)
			{
				jsonObject.add("status", ERROR.INCOMPLETE_PARAMETERS);
				jsonObject.add("message", "Incomplete Parameters provided in request.");
				JsonObject jsonObj = jsonObject.build();
	            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			}
			
			
			swpService = serviceLocator.getSwpService();
			Application app = Application.getInstance(swpService);
			JSONObject verifyJ = UtilityHelper.verifyToken(token, app);
			if(verifyJ.length()==0 || (verifyJ.length()>0 && verifyJ.has("active") && verifyJ.getInt("active")==0))
			{
				jsonObject.add("status", ERROR.FORCE_LOGOUT_USER);
				jsonObject.add("message", "Your session has expired. Please log in again");
				JsonObject jsonObj = jsonObject.build();
	            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			}
			
			
			String hql = "Select tp from Station tp WHERE tp.deletedAt IS NULL AND tp.client.clientCode = '" + clientCode + "' " +
					"AND tp.deletedAt IS NULL";
			if(stationCode!=null)
				hql = hql + " AND tp.stationCode = '" + stationCode + "'";
			Station station= (Station)this.swpService.getUniqueRecordByHQL(hql);
			jsonObject.add("message", "Station Found");
			jsonObject.add("status", ERROR.GENERAL_SUCCESS);
			jsonObject.add("station", new Gson().toJson(station));
			JsonObject jsonObj = jsonObject.build();
            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			
		}catch(Exception e)
		{
			log.warn(e); log.error("exception...", e);
			e.printStackTrace();
			JsonObject jsonObj = jsonObject.build();
            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
		}
	}
	
	
	
	public Response deleteStation(String stationId, String token, String requestId, String clientCode, String ipAddress) {
		// TODO Auto-generated method stub
		JsonObjectBuilder jsonObject = Json.createObjectBuilder();
    	
    	
		try{
			
			if(stationId==null || clientCode==null)
			{
				jsonObject.add("status", ERROR.INCOMPLETE_PARAMETERS);
				jsonObject.add("message", "Incomplete Parameters provided in request.");
				JsonObject jsonObj = jsonObject.build();
	            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			}
			
			
			swpService = serviceLocator.getSwpService();
			Application app = Application.getInstance(swpService);
			JSONObject verifyJ = UtilityHelper.verifyToken(token, app);
			if(verifyJ.length()==0 || (verifyJ.length()>0 && verifyJ.has("active") && verifyJ.getInt("active")==0))
			{
				jsonObject.add("status", ERROR.FORCE_LOGOUT_USER);
				jsonObject.add("message", "Your session has expired. Please log in again");
				JsonObject jsonObj = jsonObject.build();
	            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			}
			
			
			String username = verifyJ.has("username") ? verifyJ.getString("username") : null;
			//log.inforequestId + "username ==" + (username==null ? "" : username));
			String roleCode_ = verifyJ.has("roleCode") ? verifyJ.getString("roleCode") : null;
			//log.inforequestId + "roleCode_ ==" + (roleCode_==null ? "" : roleCode_));
			User user = null;
			RoleType roleCode = null;
			
			if(roleCode_ != null)
			{
				roleCode = RoleType.valueOf(roleCode_);
			}
			
			if(username!=null)
			{
				user = (User)this.swpService.getUniqueRecordByHQL("select tp from User tp where tp.username = '" + username + "' AND " +
						"tp.userStatus = " + UserStatus.ACTIVE.ordinal() + " AND tp.deletedAt IS NULL AND tp.client.clientCode = '"+clientCode+"'");
				//roleCode = user.getRoleCode();
			}
			
			
			String hql = "Select tp from Station tp WHERE tp.stationId = '" + stationId + "' AND tp.client.clientCode = '" + clientCode + "' AND tp.deletedAt IS NULL";
			Station station= (Station)this.swpService.getUniqueRecordByHQL(hql);
			
			if(station!=null)
			{
				station.setDeletedAt(new Date());
				swpService.updateRecord(station);
			}
			
			AuditTrail ad = UtilityHelper.createAuditTrailEntry(ipAddress, RequestType.STATION_DELETE, requestId, this.swpService, 
					verifyJ.has("username") ? verifyJ.getString("username") : null, station.getStationId(), Station.class.getName(), 
					"Delete Station. Station name: " + station.getStationName() + " | Deleted By " + user.getFirstName() + " " + user.getLastName(), clientCode);
			
			
			jsonObject.add("message", "Station deleted successfully");
			jsonObject.add("status", ERROR.GENERAL_SUCCESS);
			jsonObject.add("terminal", new Gson().toJson(station));
			JsonObject jsonObj = jsonObject.build();
            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
			
		}catch(Exception e)
		{
			log.warn(e); log.error("exception...", e);
			e.printStackTrace();
			JsonObject jsonObj = jsonObject.build();
            return UtilityHelper.getNoCacheResponseBuilder( Response.Status.OK ).entity( jsonObj.toString() ).build();
		}
	}
	
}