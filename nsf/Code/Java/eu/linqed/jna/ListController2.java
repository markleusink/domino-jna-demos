package eu.linqed.jna;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.mindoo.domino.jna.NotesCollection;
import com.mindoo.domino.jna.NotesCollection.Direction;
import com.mindoo.domino.jna.NotesCollection.EntriesAsListCallback;
import com.mindoo.domino.jna.NotesDatabase;
import com.mindoo.domino.jna.NotesViewEntryData;
import com.mindoo.domino.jna.constants.Navigate;
import com.mindoo.domino.jna.constants.ReadMask;

public class ListController2 implements Serializable {
	
	private static final long serialVersionUID = 1L;

	static String fakenamesPath = "jna/fakenames2018.nsf";
	
	List<Map> entries = new ArrayList();
	
	int NUM_PER_PAGE = 15;		// number of entries to show per page
	int skipEntries = 1; 		// number of entries to skip (used for pagination)
	int totalEntries;			// total entries
	
	String sortColumn = "firstName";		//default sort column
	boolean sortAscending = true;
	
	public ListController2() {
		
		//load first set of entires
		loadEntries();
		
	}
	
	public void loadEntries() {
		
		try {
			NotesDatabase db = new NotesDatabase(ExtLibUtil.getCurrentSession(), "", fakenamesPath);
			NotesCollection collection = db.openCollectionByName("contacts");
			
			//resort the collection (view) according to the selected column
			collection.resortView(this.sortColumn, (this.sortAscending ? Direction.Ascending : Direction.Descending));
			
			totalEntries = collection.getTopLevelEntries();
			
			// tell the API how to navigate in the view: from one entry in the selectedList
			// to the next one (in view ordering)
			EnumSet<Navigate> returnNavigator = EnumSet.of(Navigate.NEXT);
			
			// tell the API which data we want to read (in this case note ids and column itemname/value map)
			EnumSet<ReadMask> returnData = EnumSet.of(ReadMask.NOTEID, ReadMask.SUMMARYVALUES);
			
			//get a list of 'NotesViewEntryData' for the current page
			List<NotesViewEntryData> viewEntries = collection.getAllEntries("0", skipEntries, returnNavigator, NUM_PER_PAGE, returnData, 
					new EntriesAsListCallback(NUM_PER_PAGE));
			
			//transform the list into a list of map (where every entry represents a view entry and has a map
			//containing the column values)
			entries.clear();
			
			for( NotesViewEntryData entry : viewEntries) {
				entries.add( entry.getColumnDataAsMap() );
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
	
	//check if there are more pages to show
	public boolean isHasMorePages() {
		return (this.skipEntries + this.NUM_PER_PAGE - 1) < this.totalEntries;
	}

	//first entry index on the page
	public int getPageStartEntry() {
		return this.skipEntries;
	}
	
	//last entry index on the page
	public int getPageEndEntry() {
		int end = this.skipEntries + NUM_PER_PAGE - 1;
		if (end > this.totalEntries) {
			end = this.totalEntries;
		}
		return end;
	}
	
	//navigate to the next page
	public void nextPage() {
		this.skipEntries += NUM_PER_PAGE;
		loadEntries();
	}

	//navigate to the previous page
	public void previousPage() {
		this.skipEntries -= NUM_PER_PAGE;
		if (this.skipEntries < 1) {
			skipEntries = 1;
		}
		loadEntries();
	}
	
	//sort column
	public String getSortColumn() {
		return sortColumn;
	}
	
	public void setSortColumn(String sortColumn) {
		
		if (sortColumn.equals(this.sortColumn) ) {
			//column clicked that's already sorted, changed the order
			this.sortAscending = !this.sortAscending;
		} else {
			this.sortColumn = sortColumn;
			this.sortAscending = true;
		}
		
		loadEntries();
		
	}
	
	public boolean isSortAscending() {
		return sortAscending;
	}
	
}
