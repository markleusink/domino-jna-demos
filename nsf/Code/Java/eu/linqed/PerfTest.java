package eu.linqed;

import java.util.EnumSet;
import java.util.List;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.mindoo.domino.jna.NotesCollection;
import com.mindoo.domino.jna.NotesDatabase;
import com.mindoo.domino.jna.NotesViewEntryData;
import com.mindoo.domino.jna.constants.Navigate;
import com.mindoo.domino.jna.constants.ReadMask;

public class PerfTest {

	static String fakenamesPath = "jna/fakenames2018.nsf";
	static String category = "M";

	public static void getCountCountingIds() {

		NotesDatabase db = new NotesDatabase(ExtLibUtil.getCurrentSession(), "", fakenamesPath);
		NotesCollection collection = db.openCollectionByName("contacts by ln letter");

		long start = System.currentTimeMillis();

		System.out.println(">>" + collection.getAllIdsInCategory(category, EnumSet.of(Navigate.NEXT)).size());
		System.out.println("done in " + (System.currentTimeMillis() - start) + "ms");

	}

	public static void getCountFromCategory() {

		NotesDatabase db = new NotesDatabase(ExtLibUtil.getCurrentSession(), "", fakenamesPath);
		NotesCollection collection = db.openCollectionByName("contacts by ln letter");

		long start = System.currentTimeMillis();

		List<NotesViewEntryData> catEntries = collection.getAllEntriesInCategory(category, 0,
				EnumSet.of(Navigate.CURRENT),
				1,
				EnumSet.of(
						ReadMask.INDEXCHILDREN,
						ReadMask.INDEXDESCENDANTS,
						ReadMask.INDEXSIBLINGS),
				new NotesCollection.EntriesAsListCallback(1));

		if (!catEntries.isEmpty()) {

			NotesViewEntryData catEntry = catEntries.get(0);
			int descendants = catEntry.getDescendantCount();
			int siblings = catEntry.getSiblingCount();
			int children = catEntry.getChildCount();

			System.out.println("Category found: " + category);
			System.out.println("descendants: " + descendants);
			System.out.println("siblings: " + siblings);
			System.out.println("children: " + children);
			
		} else {
			
			System.out.println("Category not found: " + category);
			
		}

		System.out.println("done in " + (System.currentTimeMillis() - start) + "ms");

	}

}
