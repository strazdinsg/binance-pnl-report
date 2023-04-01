package no.strazdins.data;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Extra user-provided information.
 */
public class ExtraInfo {
  // Mapping timestamp to a list of extra info entries
  private final Map<Long, List<ExtraInfoEntry>> entries = new TreeMap<>();
  // Copy of all the entries
  private final List<ExtraInfoEntry> allEntries = new LinkedList<>();

  /**
   * Add an entry to the info storage.
   *
   * @param infoEntry The info entry to add
   */
  public void add(ExtraInfoEntry infoEntry) {
    List<ExtraInfoEntry> entryList = entries.get(infoEntry.utcTimestamp());
    if (entryList == null) {
      entryList = new LinkedList<>();
      entries.put(infoEntry.utcTimestamp(), entryList);
    }
    entryList.add(infoEntry);
    allEntries.add(infoEntry);
  }

  /**
   * Check if the extra info set is empty.
   *
   * @return True if no info is stored here, false otherwise
   */
  public boolean isEmpty() {
    return entries.isEmpty();
  }

  /**
   * Get all entries stored here.
   *
   * @return All the extra info entries, ordered by timestamp.
   */
  public List<ExtraInfoEntry> getAllEntries() {
    return allEntries;
  }

  /**
   * Check if this information storage contains the provided entry.
   *
   * @param e The entry to check
   * @return True if this info storage contains the requested info entry, false otherwise
   */
  public boolean contains(ExtraInfoEntry e) {
    boolean found = false;
    Iterator<ExtraInfoEntry> it = getAllEntries().iterator();
    while (!found && it.hasNext()) {
      ExtraInfoEntry existingEntry = it.next();
      found = existingEntry.utcTimestamp() == e.utcTimestamp()
          && existingEntry.type().equals(e.type());
    }
    return found;
  }

  /**
   * Get stored extra info for a given time moment.
   *
   * @param utcTime UTC timestamp of the time moment in question, including milliseconds.
   * @return The stored ExtraInfo record, or null if none found.
   */
  public ExtraInfoEntry getAtTime(long utcTime) {
    List<ExtraInfoEntry> entryList = entries.get(utcTime);
    return entryList != null ? entryList.get(0) : null;
  }
}