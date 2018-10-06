package eu.linqed.jna;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;

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

public class ListController5 implements Serializable {

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

	List<String> filterCountry = new ArrayList<String>();
	List<String> countries = new ArrayList<String>(); // list of typeahead suggestions

	String filterLastname = "";

	LinkedHashSet<Integer> matchingIds = new LinkedHashSet<Integer>();

	private transient NotesCollection collection;

	@SuppressWarnings("unchecked")
	public ListController5() {

		// load city typeahead options: get a list of all cities used in the view
		// the list is cached in the applicationScope

		if (ExtLibUtil.getApplicationScope().containsKey("cities")) {

			this.cities = (List<String>) ExtLibUtil.getApplicationScope().get("cities");

		} else {
			long start = System.currentTimeMillis();

			// get a list of all cities for all contacts
			// this uses an optimised view column lookup formula from JNA:
			Set<String> cities = getCollection().getColumnValues("city", null);
			this.cities.addAll(cities);

			ExtLibUtil.getApplicationScope().put("cities", this.cities);

			System.out.println("cities now has " + cities.size() + " in " + (System.currentTimeMillis() - start) + "ms");
		}

		// load country typeahead options: get a list of all cities used in the view
		// the list is cached in the applicationScope

		if (ExtLibUtil.getApplicationScope().containsKey("countries")) {

			this.countries = (List<String>) ExtLibUtil.getApplicationScope().get("countries");

		} else {
			long start = System.currentTimeMillis();

			// get a list of all cities for all contacts
			// this uses an optimised view column lookup formula from JNA:
			Set<String> countries = getCollection().getColumnValues("country", null);
			this.countries.addAll(countries);

			ExtLibUtil.getApplicationScope().put("countries", this.countries);

			System.out.println("countries now has " + countries.size() + " in " + (System.currentTimeMillis() - start) + "ms");
		}

		// load first set of entries
		loadEntries();

	}

	// Retrieve the collection (a.k.a view) we use in this class.
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

			// if a filter was set, we need to change a couple of things:
			// - just read the contacts that match the filters
			// - use a different navigator: we loop only over the selected contact
			// in the collection
			boolean hasFilters = !filterCity.isEmpty() || !filterCountry.isEmpty() || StringUtil.isNotEmpty(filterLastname);

			if (hasFilters) {

				// apply the matching Note IDs
				collection.select(matchingIds, true);

				returnNavigator = EnumSet.of(Navigate.NEXT_SELECTED);

				totalEntries = collection.getSelectedList().getCount();

			} else {

				returnNavigator = EnumSet.of(Navigate.NEXT);
				totalEntries = collection.getTopLevelEntries();

			}

			// get a list of 'NotesViewEntryData' for the current page
			List<NotesViewEntryData> viewEntries = collection.getAllEntries("0", skipEntries, returnNavigator, NUM_PER_PAGE, returnData,
					new EntriesAsListCallback(NUM_PER_PAGE));

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

		this.filterCity = tmp;

	}

	public Object getFilterCountry() {
		return filterCountry;
	}

	@SuppressWarnings("unchecked")
	public void setFilterCountry(Object filterCountry) {

		List<String> tmp = new ArrayList<String>();

		if (filterCountry instanceof String) {
			if (StringUtil.isNotEmpty((String) filterCountry)) {
				tmp.add((String) filterCountry);
			}
		} else {

			// remove empties
			tmp = (List<String>) filterCountry;
			tmp.removeAll(Arrays.asList(""));

		}

		this.filterCountry = tmp;
	}

	public void applyFilters() {
		
	

		try {
			this.skipEntries = 1; // go to first page

			// get the contacts view and sort it using the property we're filtering on
			NotesCollection collection = getCollection();

			matchingIds.clear();

			// for every city entered, we find the Note IDs of the entries in the contacts view
			// that match. All IDs are stored in a Set (matchingIds)
			if (!filterCity.isEmpty()) {

				collection.resortView("city", Direction.Ascending);

				for (String city : filterCity) {
					Set<Integer> matches = collection.getAllIdsByKey(EnumSet.of(Find.CASE_INSENSITIVE), city);

					matchingIds.addAll(matches);
					System.out.println("- added " + matches.size() + " for city " + city);
				}
			}

			// do the same for country

			if (!filterCountry.isEmpty()) {

				collection.resortView("country", Direction.Ascending);

				for (String country : filterCountry) {
					Set<Integer> matches = collection.getAllIdsByKey(EnumSet.of(Find.CASE_INSENSITIVE), country);

					matchingIds.addAll(matches);
					System.out.println("- added " + matches.size() + " for city " + country);
				}
			}

			// lastname
			if (StringUtils.isNotEmpty(filterLastname)) {

				// get a list of all lastnames

				Set<String> lastNames = new TreeSet<String>();

				if (ExtLibUtil.getApplicationScope().containsKey("lastNames")) {

					lastNames = (TreeSet<String>) ExtLibUtil.getApplicationScope().get("lastNames");

				} else {
					long start = System.currentTimeMillis();

					// get a list of all cities for all contacts
					// this uses an optimised view column lookup formula from JNA:
					lastNames = getCollection().getColumnValues("$lastNameNoteId", null);

					ExtLibUtil.getApplicationScope().put("lastNames", lastNames);

					System.out.println("lastNames now has " + lastNames.size() + " in " + (System.currentTimeMillis() - start) + "ms");
				}
				
				int num = 0;
				
				System.out.println("check for  " + filterLastname);

				// find matches
				for (String l : lastNames) {
					String name = l.split("\\|")[0].toLowerCase();
					String noteId = l.split("\\|")[1];

					if (name.indexOf(filterLastname) > -1) {
						
						num++;
						Integer id = Integer.parseInt(noteId, 16);
						matchingIds.add(id);
					}

				}
				
				System.out.println("added " + num);

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
		this.filterCountry.clear();
		this.filterLastname = "";
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

	public List<String> getCountries() {
		return countries;
	}

	public String getFilterLastname() {
		return filterLastname;
	}

	public void setFilterLastname(String filterLastname) {
		this.filterLastname = filterLastname;
	}

}
