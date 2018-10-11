package eu.linqed.jna;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.mindoo.domino.jna.NotesCollection;
import com.mindoo.domino.jna.NotesCollection.EntriesAsListCallback;
import com.mindoo.domino.jna.NotesDatabase;
import com.mindoo.domino.jna.NotesViewEntryData;
import com.mindoo.domino.jna.constants.Find;
import com.mindoo.domino.jna.constants.Navigate;
import com.mindoo.domino.jna.constants.ReadMask;

public class ListControllerFixed implements Serializable {

	private static final long serialVersionUID = 1L;

	static String fakenamesPath = "jna/fakenames2018.nsf";

	List<Map> entries = new ArrayList();

	int NUM_PER_PAGE = 15; // number of entries to show per page
	int skipEntries = 1; // number of entries to skip (used for pagination)
	int totalEntries; // total entries

	String sortColumn = "firstName"; // default sort column
	boolean sortAscending = true;

	// filters
	List<String> filterCity = new ArrayList<String>();
	List<String> cities = new ArrayList<String>(); // list of typeahead suggestions
	LinkedHashSet<Integer> matchingIds = new LinkedHashSet<Integer>();

	@SuppressWarnings("unchecked")
	public ListControllerFixed() {

		// load city typeahead options: get a list of all cities used in the view
		// the list is cached in the applicationScope

		if (ExtLibUtil.getApplicationScope().containsKey("cities")) {

			this.cities = (List<String>) ExtLibUtil.getApplicationScope().get("cities");

		} else {
			long start = System.currentTimeMillis();

			// get a list of all cities for all contacts
			// this uses an optimised view column lookup formula from JNA:
			NotesDatabase db = new NotesDatabase(ExtLibUtil.getCurrentSession(), "", fakenamesPath);
			NotesCollection collection = db.openCollectionByName("contactsCity");
			Set<String> cities = collection.getColumnValues("city", null);
			this.cities.addAll(cities);

			ExtLibUtil.getApplicationScope().put("cities", this.cities);

			System.out.println("cities now has " + cities.size() + " in " + (System.currentTimeMillis() - start) + "ms");
		}

		// load first set of entries
		loadEntries();

	}

	public void loadEntries() {

		try {
			NotesDatabase db = new NotesDatabase(ExtLibUtil.getCurrentSession(), "", fakenamesPath);
			NotesCollection collection = db.openCollectionByName("contactsLN");
		
			// resort the collection (view) according to the selected column
			//collection.resortView(this.sortColumn, (this.sortAscending ? Direction.Ascending : Direction.Descending));

			// tell the API which data we want to read (in this case note ids and column itemname/value map)
			EnumSet<ReadMask> returnData = EnumSet.of(ReadMask.NOTEID, ReadMask.SUMMARYVALUES);
			EnumSet<Navigate> returnNavigator;

			// if a filter was set, we need to change a couple of things:
			// - just read the contacts that match the filters
			// - use a different navigator: we loop only over the selected contact
			// in the collection
			boolean hasFilters = !filterCity.isEmpty();

			if (hasFilters) {

				// apply the matching Note IDs and update the navigator
				collection.select(matchingIds, true);
				returnNavigator = EnumSet.of(Navigate.NEXT_SELECTED);
				totalEntries = collection.getSelectedList().getCount();

			} else {

				returnNavigator = EnumSet.of(Navigate.NEXT);
				totalEntries = collection.getTopLevelEntries();

			}

			long start = System.currentTimeMillis();
			
			// get a list of 'NotesViewEntryData' for the current page
			List<NotesViewEntryData> viewEntries = collection.getAllEntries("0", skipEntries, returnNavigator, NUM_PER_PAGE, returnData,
					new EntriesAsListCallback(NUM_PER_PAGE));

			long end = System.currentTimeMillis();
			
			System.out.println("done reading of " + totalEntries + " in loadEntries in " + (end-start) + "ms");
			
			// transform the list into a list of maps (where every entry
			// represents a view entry and has a map containing the column values)
			entries.clear();

			for (NotesViewEntryData entry : viewEntries) {
				entries.add(entry.getColumnDataAsMap());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public List<Map> getEntries() {
		return entries;
	}

	public int getSkipEntries() {
		return skipEntries;
	}

	public int getTotalEntries() {
		return totalEntries;
	}

	// check if there are more pages to show
	public boolean isHasMorePages() {
		return (this.skipEntries + this.NUM_PER_PAGE - 1) < this.totalEntries;
	}

	// first entry index on the page
	public int getPageStartEntry() {
		return this.skipEntries;
	}

	// last entry index on the page
	public int getPageEndEntry() {
		int end = this.skipEntries + NUM_PER_PAGE - 1;
		if (end > this.totalEntries) {
			end = this.totalEntries;
		}
		return end;
	}

	// navigate to the next page
	public void nextPage() {
		this.skipEntries += NUM_PER_PAGE;
		loadEntries();
	}

	// navigate to the previous page
	public void previousPage() {
		this.skipEntries -= NUM_PER_PAGE;
		if (this.skipEntries < 1) {
			skipEntries = 1;
		}
		loadEntries();
	}

	// sort column
	public String getSortColumn() {
		return sortColumn;
	}

	public void setSortColumn(String sortColumn) {

		if (sortColumn.equals(this.sortColumn)) {
			// column clicked that's already sorted, changed the order
			this.sortAscending = !this.sortAscending;
			this.skipEntries = 1; // go to first page
		} else {
			this.sortColumn = sortColumn;
			this.sortAscending = true;
			this.skipEntries = 1; // go to first page
		}

		loadEntries();

	}

	public boolean isSortAscending() {
		return sortAscending;
	}

	// filter
	public Object getFilterCity() {
		return filterCity;
	}

	@SuppressWarnings("unchecked")
	public void setFilterCity(Object filterCity) {
		//only update the value/ apply the filter it the value chnanged

		List<String> tmp = new ArrayList<String>();

		if (filterCity instanceof String) {
			if (StringUtil.isNotEmpty((String) filterCity)) {
				tmp.add((String) filterCity);
			}
		} else {

			// remove empties
			tmp = (List<String>) filterCity;
			tmp.removeAll(Arrays.asList(""));

		}

		if (!tmp.equals(this.filterCity)) {
			this.filterCity = tmp;
			applyFilters();
		}
	}

	public void applyFilters() {

		try {
			this.skipEntries = 1; // go to first page

			// get the contacts view and sort it using the property we're filtering on
			NotesDatabase db = new NotesDatabase(ExtLibUtil.getCurrentSession(), "", fakenamesPath);
			NotesCollection collection = db.openCollectionByName("contactsCity");
		
			matchingIds.clear();

			// for every city entered, we find the Note IDs of the entries in the contacts view
			// that match. All IDs are stored in a Set (matchingIds)
			if (!filterCity.isEmpty()) {

				for (String city : filterCity) {
					Set<Integer> matches = collection.getAllIdsByKey(EnumSet.of(Find.CASE_INSENSITIVE), city);
					matchingIds.addAll(matches);
				}
			}

			loadEntries();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void clearFilters() {
		this.matchingIds.clear();
		this.skipEntries = 1;
		this.filterCity.clear();
		loadEntries();
	}

	// return the options for the city filter typeahead
	public List<String> getCities() {
		return cities;
	}

	// returns 100 entries from the list of cities
	public List<String> getCitiesLimit() {

		List<String> res = new ArrayList<String>();
		int i = 0;

		while (i < 100) {
			i++;
			res.add(cities.get(i * 60));
		}

		return res;
	}


}