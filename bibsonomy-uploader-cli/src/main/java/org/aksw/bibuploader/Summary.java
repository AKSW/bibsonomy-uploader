package org.aksw.bibuploader;

import java.util.HashSet;
import java.util.Set;

public class Summary {
	private Set<String> duplicates;
	private int sucAdded;
	private Set<String> failedAdditions;
	private int noUpdated;
	private int noRemoved;
	private Set<String> noTagEntries;

	public Summary() {
		duplicates = new HashSet<String>();
		failedAdditions = new HashSet<String>();
		noTagEntries = new HashSet<String>();
	}

	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("\nDuplicates found in the given file:\n");
		for (String duplicate : duplicates)
			builder.append(duplicate.toString()).append("\n");

		builder.append("\nPosts without keywords in the given file:\n");
		for (String missingTag : noTagEntries)
			builder.append(missingTag.toString()).append("\n");

		builder.append("\nSummary:\n");
		builder.append(duplicates.size()).append("\tDuplicates found in the given file (see titles above)\n");
		builder.append(noTagEntries.size()).append("\tPosts without keywords in the given file (see titles above)\n");
		builder.append(sucAdded).append("\tPapers were added to bibsonomy\n");
		builder.append(failedAdditions.size()).append("\tPapers couldn't be added\n");
		
		builder.append(noRemoved).append("\tPapers were deleted from bibsonomy\n");
		builder.append(noUpdated).append("\tPapers were updated\n\n");

		return builder.toString();
	}

	public void addDuplicate(String dup) {
		duplicates.add(dup);
	}

	public void addNoTagEntry(String missingTagEntry) {
		noTagEntries.add(missingTagEntry);
	}

	public void addSucAdd() {
		sucAdded++;
	}

	public void addFailAdd(String failed) {
		failedAdditions.add(failed);
	}

	public void addUpdate() {
		noUpdated++;
	}

	public void setRemoved(int removed) {
		noRemoved = removed;
	}
}
