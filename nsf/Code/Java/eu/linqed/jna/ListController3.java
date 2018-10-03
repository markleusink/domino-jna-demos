package eu.linqed.jna;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.mindoo.domino.jna.NotesCollection;
import com.mindoo.domino.jna.NotesCollection.Direction;
import com.mindoo.domino.jna.NotesCollection.EntriesAsListCallback;
import com.mindoo.domino.jna.NotesDatabase;
import com.mindoo.domino.jna.NotesViewEntryData;
import com.mindoo.domino.jna.constants.Find;
import com.mindoo.domino.jna.constants.Navigate;
import com.mindoo.domino.jna.constants.ReadMask;

public class ListController3 implements Serializable {

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

	private transient NotesCollection collection;

	public ListController3() {

		// load city typeahead options: get a list of all cities used in the view
		long start = System.currentTimeMillis();

		// get all the cities and convert to a list
		Set<String> cities = getCollection().getColumnValues("city", null);
		this.cities.addAll(cities);

		System.out.println("cities now has " + cities.size() + " in " + (System.currentTimeMillis() - start) + "ms");

		// load first set of entries
		loadEntries();

	}

	// Retrieve the collection (a.k.a view) to use
	// The collection is stored in this class as a transient variable for
	// faster access
	private NotesCollection getCollection() {

		if (collection == null) {
			NotesDatabase db = new NotesDatabase(ExtLibUtil.getCurrentSession(), "", fakenamesPath);
			collection = db.openCollectionByName("contacts");
		}

		return collection;
	}

	public void loadEntries() {

		System.out.println("load entries...");

		try {
			NotesCollection collection = getCollection();

			// resort the collection (view) according to the selected column
			collection.resortView(this.sortColumn, (this.sortAscending ? Direction.Ascending : Direction.Descending));

			// tell the API which data we want to read (in this case note ids and column itemname/value map)
			EnumSet<ReadMask> returnData = EnumSet.of(ReadMask.NOTEID, ReadMask.SUMMARYVALUES);
			EnumSet<Navigate> returnNavigator;

			boolean hasFilters = !filterCity.isEmpty();

			if (hasFilters) {

				// apply the matching Note IDs
				collection.select(matchingIds, true);

				// note that if we're applying a selection, we need to use a different
				// 'navigator', moving over every 'selected' entry
				returnNavigator = EnumSet.of(Navigate.NEXT_SELECTED);

				totalEntries = collection.getSelectedList().getCount();

			} else {
				returnNavigator = EnumSet.of(Navigate.NEXT);
				totalEntries = collection.getTopLevelEntries();

			}

			// get a list of 'NotesViewEntryData' for the current page
			List<NotesViewEntryData> viewEntries = collection.getAllEntries("0", skipEntries, returnNavigator, NUM_PER_PAGE, returnData,
					new EntriesAsListCallback(NUM_PER_PAGE));

			// transform the list into a list of map (where every entry represents a view entry and has a map
			// containing the column values)
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

	public void setFilterCity(Object filterCity) {

		List<String> cities = new ArrayList<String>();
		;

		if (filterCity instanceof String) {
			if (StringUtil.isNotEmpty((String) filterCity)) {
				cities.add((String) filterCity);
			}
		} else {

			// remove empties
			for (String city : (List<String>) filterCity) {
				if (StringUtil.isNotEmpty(city.trim())) {
					cities.add(city.trim());
				}
			}
		}

		this.filterCity = cities;

	}

	public void applyFilters() {
		
		System.out.println("apply filters...");

		this.skipEntries = 1; // go to first page

		// get the contacts view and sort it using the property we're filtering on
		NotesCollection collection = getCollection();
		collection.resortView("city", Direction.Ascending);

		matchingIds.clear();

		// for every city entered, we find the Note IDs of the entries in the contacts view
		// that match. All IDs are stored in a Set (matchingIds)

		for (String city : filterCity) {
			matchingIds.addAll(collection.getAllIdsByKey(EnumSet.of(Find.CASE_INSENSITIVE), city));
			System.out.println("- added " + matchingIds.size() + " for city " + city);
		}

		loadEntries();
		
	}

	public void clearFilters() {
		this.matchingIds.clear();
		this.filterCity.clear();
		loadEntries();
	}

	// return the options for the city filter typeahead
	public List<String> getCities() {
		return cities;
	}

	// returns 50 entries from the list of cities
	public List<String> getCitiesLimit() {
		List<String> res = new ArrayList<String>();
		int i=0; 
		
		while (i<50) {
			i++;
			res.add(cities.get(i*100));
		}
		return res;
	}

}