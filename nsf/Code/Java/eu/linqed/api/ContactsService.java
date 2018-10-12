package eu.linqed.api;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.json.JsonGenerator;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.domino.services.ServiceException;
import com.ibm.domino.services.rest.RestServiceEngine;
import com.ibm.xsp.extlib.component.rest.CustomService;
import com.ibm.xsp.extlib.component.rest.CustomServiceBean;

public class ContactsService extends CustomServiceBean {

	@Override
	public void renderService(CustomService service, RestServiceEngine engine) throws ServiceException {

		HttpServletRequest request = engine.getHttpRequest();
		HttpServletResponse response = engine.getHttpResponse();
		
		try {
			
			response.setContentType("application/json");
			
			// service to send a JSON object containing a list of contacts back to the browser
			
			// get request parameters:
			
			//index to start reading
			int startIndex = getParameterAsInt(request, "start", 1);
			
			//number of entries to return
			int numToReturn = getParameterAsInt(request, "count", 20);
			
			//sort column & order
			String sortCol = getParameter(request, "sortCol", "firstName");
			boolean sortAsc = getParameterAsBoolean(request, "sortAsc", true);
			
			//filter
			String filter = getParameter(request, "filter", "");
		
			//set up the generic result object
			Map<String, Object> json = new HashMap<String, Object>();
			json.put("message", "");
			json.put("error", false);
			json.putAll(ContactsController.getEntries(startIndex, numToReturn, sortCol, sortAsc, filter));
	
			//send result to the browser
			Writer writer = response.getWriter();
			writer.write(JsonGenerator.toJson(JsonJavaFactory.instance, json));
			writer.close();

		} catch (Exception e) {
			
			//send error object as response
			Map<String, Object> json = new HashMap<String, Object>();
			json.put("message", e.getMessage());
			json.put("error", true);
			
			try {
				Writer writer = response.getWriter();
				writer.write(JsonGenerator.toJson(JsonJavaFactory.instance, json));
				writer.close();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		
		}
	}
	
	/*
	 * Helper functions to read URL parameters
	 */
	
	private static boolean getParameterAsBoolean(HttpServletRequest request, String parameter, boolean defaultValue) {
		return Boolean.valueOf(getParameter(request, parameter, String.valueOf(defaultValue)));
	}

	private static String getParameter(HttpServletRequest request, String parameter, String defaultValue) {

		String pm = request.getParameter(parameter);
		if (StringUtil.isEmpty(pm)) {
			return defaultValue;
		}

		return pm;
	}

	private static int getParameterAsInt(HttpServletRequest request, String parameter, int defaultValue) {

		String pm = request.getParameter(parameter);
		if (StringUtil.isEmpty(pm)) {
			return defaultValue;
		}

		return Integer.valueOf(pm);
	}

}
