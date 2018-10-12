package eu.linqed.api;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.json.JsonGenerator;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.mindoo.domino.jna.NotesCollection;
import com.mindoo.domino.jna.NotesCollection.Direction;
import com.mindoo.domino.jna.NotesCollection.EntriesAsListCallback;
import com.mindoo.domino.jna.NotesDatabase;
import com.mindoo.domino.jna.NotesViewEntryData;
import com.mindoo.domino.jna.constants.Navigate;
import com.mindoo.domino.jna.constants.ReadMask;

class ContactsController {

	static String fakenamesPath = "jna/fakenames2018.nsf";

	public static Map<String, Object>  getEntries(int startIndex, int numToReturn, String sortColumn, boolean sortAscending, String query) {

		int totalEntries; // total entries
		Map<String, Object> result = new HashMap<String, Object>();

		try {
			
			//open target view/ collection
			NotesDatabase db = new NotesDatabase(ExtLibUtil.getCurrentSession(), "", fakenamesPath);
			NotesCollection collection = db.openCollectionByName("contacts");

			// resort according to the selected column
			collection.resortView(sortColumn, (sortAscending ? Direction.Ascending : Direction.Descending));

			// tell the API which data we want to read (in this case note ids and column itemname/value map)
			EnumSet<ReadMask> returnData = EnumSet.of(ReadMask.NOTEID, ReadMask.SUMMARYVALUES);
			EnumSet<Navigate> returnNavigator;

			// if a filter was set, we need to change a couple of things:
			// - just read the contacts that match the filter
			// - use a different navigator: we loop only over the selected contact
			//   in the collection
			boolean hasFilters = StringUtil.isNotEmpty(query);

			if (hasFilters) {

				// apply the matching Note IDs and update the navigator
				collection.select(getQueryMatchingIds(collection, query), true);
				
				returnNavigator = EnumSet.of(Navigate.NEXT_SELECTED);
				totalEntries = collection.getSelectedList().getCount();

			} else {

				returnNavigator = EnumSet.of(Navigate.NEXT);
				totalEntries = collection.getTopLevelEntries();

			}

			// get a list of 'NotesViewEntryData' for the current page
			List<NotesViewEntryData> viewEntries = collection.getAllEntries("0", startIndex, returnNavigator, numToReturn, returnData,
					new EntriesAsListCallback(numToReturn));

			List<Map> entries = new ArrayList();
			
			// transform the list into a list of maps (where every entry
			// represents a view entry and has a map containing the column values)
			
			for (NotesViewEntryData entry : viewEntries) {

				// the JSON Generator that we're using in the ContactsService class can't handle
				// Date or Calendar objects. So before returning the map, we convert them to an ISO8601
				// strings
				Map<String, Object> tmp = entry.getColumnDataAsMap();

				// remove what where not interested in
				tmp.remove("$query");
				tmp.remove("$lastnamenoteid");

				// get all the properties that contain a date
				
				Set<String> dates = new HashSet<String>();
				for (Map.Entry ee : tmp.entrySet()) {
					if (ee.getValue() instanceof GregorianCalendar) {
						dates.add((String) ee.getKey());
					}
				}

				// replace dates by ISO8601 (string) representation
				for (String dateKey : dates) {
					Calendar cal = (Calendar) tmp.get(dateKey);
					tmp.put(dateKey, JsonGenerator.dateToString(cal.getTime()));
				}

				// add note id to the response
				tmp.put("noteId", entry.getNoteId());
				
				entries.add(tmp);
			}
			
			result.put("entries", entries);
			result.put("total", totalEntries);
			result.put("start", startIndex);
			result.put("count", numToReturn);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;

	}

	/*
	 * Returns a list of Note IDs of documents that match the query entered
	 */
	private static Set<Integer> getQueryMatchingIds(final NotesCollection collection, final String query) {

		Set<Integer> matchingIds = new HashSet<Integer>();

		try {

			if (StringUtil.isNotEmpty(query)) {

				// get a (cached) list of all lastnames
				Set<String> queryData = new TreeSet<String>();

				//long start = System.currentTimeMillis();

				if (ExtLibUtil.getApplicationScope().containsKey("queryData")) {

					queryData = (TreeSet<String>) ExtLibUtil.getApplicationScope().get("queryData");

				} else {

					// get a list of the query data (= concatenated field values)
					// this uses an optimised view column lookup formula from JNA:
					queryData = collection.getColumnValues("$query", null);

					ExtLibUtil.getApplicationScope().put("queryData", queryData);
				}

				//long end = System.currentTimeMillis();

				//System.out.println("data read in " + (end - start) + "ms");

				//start = System.nanoTime();

				// find matches
				Iterator<String> it = queryData.iterator();
				while (it.hasNext()) {
					String l = it.next();
					int i = l.lastIndexOf("|");
					String q = l.substring(0, i).toLowerCase();
					String noteId = l.substring(i + 1);
					if (q.indexOf(query.toLowerCase()) > -1) {
						Integer id = Integer.parseInt(noteId, 16);
						matchingIds.add(id);
					}

				}

				//end = System.nanoTime();

				//System.out.println("data mathced in " + TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS) + "ms: " + matchingIds.size());

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return matchingIds;
	}

}
