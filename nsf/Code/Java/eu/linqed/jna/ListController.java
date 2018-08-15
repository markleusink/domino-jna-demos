package eu.linqed.jna;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.mindoo.domino.jna.NotesCollection;
import com.mindoo.domino.jna.NotesCollection.EntriesAsListCallback;
import com.mindoo.domino.jna.NotesDatabase;
import com.mindoo.domino.jna.NotesViewEntryData;
import com.mindoo.domino.jna.constants.Navigate;
import com.mindoo.domino.jna.constants.ReadMask;

public class ListController implements Serializable {
	
	private static final long serialVersionUID = 1L;

	static String fakenamesPath = "jna/fakenames2018.nsf";
	
	List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
	
	int NUM_PER_PAGE = 15;		// number of entries to show per page
	int skipEntries = 1; 		// number of entries to skip (used for pagination)
	int totalEntries;			// total entries
	
	public ListController() {
		
		//load first set of entries
		loadEntries();
		
	}
	
	public void loadEntries() {
		
		try {
			
			// open the target database/ view (collection)
			NotesDatabase db = new NotesDatabase(ExtLibUtil.getCurrentSession(), "", fakenamesPath);
			NotesCollection collection = db.openCollectionByName("contacts");
			
			// read total number of entries
			totalEntries = collection.getTopLevelEntries();
			
			// tell the API how to navigate in the view: from one entry to the next (in view ordering)
			EnumSet<Navigate> returnNavigator = EnumSet.of(Navigate.NEXT);
			
			// tell the API which data we want to read from the view
			// (in this case note ids and column itemname/value map)
			EnumSet<ReadMask> returnData = EnumSet.of(ReadMask.NOTEID, ReadMask.SUMMARYVALUES);
			
			//read the data from the view
			List<NotesViewEntryData> viewEntries = collection.getAllEntries("0", skipEntries, returnNavigator, NUM_PER_PAGE, returnData, 
					new EntriesAsListCallback(NUM_PER_PAGE));
			
			//store the view entry data in a list containing maps
			entries.clear();
			
			for( NotesViewEntryData entry : viewEntries) {
				entries.add( entry.getColumnDataAsMap() );
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	public List<Map<String, Object>> getEntries() {
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
	
	//navigate to the next page and load the entries
	public void nextPage() {
		this.skipEntries += NUM_PER_PAGE;
		loadEntries();
	}

	//navigate to the previous page and load the entries
	public void previousPage() {
		this.skipEntries -= NUM_PER_PAGE;
		if (this.skipEntries < 1) {
			skipEntries = 1;
		}
		loadEntries();
	}
	
}
