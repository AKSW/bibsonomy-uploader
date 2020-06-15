package org.aksw.bibuploader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bibsonomy.bibtex.parser.PostBibTeXParser;
import org.bibsonomy.common.enums.GroupingEntity;
import org.bibsonomy.common.enums.PostUpdateOperation;
import org.bibsonomy.model.BibTex;
import org.bibsonomy.model.Post;
import org.bibsonomy.model.Resource;
import org.bibsonomy.model.User;
import org.bibsonomy.model.enums.Order;
import org.bibsonomy.model.logic.LogicInterface;
import org.bibsonomy.rest.client.RestLogicFactory;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BibUpdater {

	private final LogicInterface logic;

	private String username;

	private String fileLocation;

	private static Log log = LogFactory.getLog(BibUpdater.class);

	public BibUpdater(String username, String apikey, String apiurl,
			String fileLocation) {

		log.debug("Creating a new BibUpdater.");
		this.username = username;
		this.fileLocation = fileLocation;

		final RestLogicFactory rlf = new RestLogicFactory();
		logic = rlf.getLogicAccess(username, apikey);

	}

	public BibUpdater(String fileLocation) {

		log.debug("Creating a new BibChecker.");
		this.fileLocation = fileLocation;
		logic = null;
	}

	public static void main(String[] args) {

		if (args.length == 1) {

			try {

				BibUpdater checker = new BibUpdater(args[0]);
				checker.loadEntriesFromFile();

			} catch (Exception e) {

			    throw new RuntimeException("Error occured:" + e.getMessage(), e);

			}

		} else if (args.length != 4) {

			log.error("call with parameters: username apikey apiurl file");

		} else {

			try {

				BibUpdater update = new BibUpdater(args[0], args[1], args[2],
						args[3]);
				// update.diffUpdate();
				// update.flushNpush();
				update.updateAccount();

			} catch (Exception e) {

				log.error("Error occured:", e);
			}

		}

	}
	
	public void deleteEntry(Post<BibTex> post) throws Exception{
		String intraHash = post.getResource().getIntraHash();
		logic.deletePosts(username, Collections.<String>singletonList(intraHash));
	}

	private void flushNpush() throws Exception {
		// load entries
		List<Post<BibTex>> fileEntries = loadEntriesFromFile();

		// get all previously posted entries
		List<Post<BibTex>> accountEntries = loadEntriesFromAccount();

		// and delete them
		for (Post<BibTex> post : accountEntries) {
			deleteEntry(post);
			log.info(post.getResource().getTitle() + " deleted");
		}

		// upload them
		for (Post<BibTex> post : fileEntries) {
			uploadEntry(post);
			log.info(post.getResource().getTitle() + " uploaded");
		}

	}

	public List<Post<BibTex>> loadEntriesFromFile() throws Exception {

		String bibtexString = IOUtils.toString(new FileInputStream(
				this.fileLocation), "UTF-8");

		PostBibTeXParser parser = new PostBibTeXParser();

		List<Post<BibTex>> posts = parser.parseBibTeXPosts(bibtexString);

		for (Post<BibTex> post : posts) {
			post.getResource().recalculateHashes();
		}

		return posts;

	}

	public List<Post<BibTex>> loadEntriesFromAccount() throws Exception {

		List<Post<BibTex>> publications = logic.getPosts(BibTex.class, GroupingEntity.USER, username, null, null, null, null, null, Order.ADDED, null, null, 0, 1000);
		return publications;

	}

	public void uploadEntry(Post<BibTex> entry) {

		entry.setUser(new User(this.username));
		
		if(entry.getTags()==null||entry.getTags().isEmpty()){
			entry.addTag("nokeyword");
			log.warn("Please add keywords for entry: " +  entry.getResource().getTitle());
		}
		
		logic.createPosts(Collections.<Post<? extends Resource>>singletonList(entry));
	}

	public void diffUpdate() throws Exception {

		// get all previously posted entries

		List<Post<BibTex>> accountEntries = loadEntriesFromAccount();

		// store the hashes conveniently
		List<String> accountHashes = new ArrayList<String>();

		for (Post<BibTex> post : accountEntries) {
			accountHashes.add(post.getResource().getIntraHash());
		}

		List<Post<BibTex>> fileEntries = loadEntriesFromFile();

		// if not already stored, upload them
		for (Post<BibTex> post : fileEntries) {
			if (!accountHashes.contains(post.getResource().getIntraHash())) {
				uploadEntry(post);
				log.info(post.getResource().getTitle() + " uploaded");
			} else {
				log.info(post.getResource().getTitle() + " was already there");
			}
		}

	}
	
	/**
	 * Updates the account entries (B) based on the file entries (F)
	 * @throws Exception
	 */
	private void updateAccount() throws Exception {
		// load entries
		List<Post<BibTex>> fileEntries = loadEntriesFromFile();
		
		// get all previously posted entries
		List<Post<BibTex>> accountEntries = loadAllEntriesFromAccount();
		
		//remove duplicates from file
		Set<String> seen = new HashSet<String>();
		fileEntries.removeIf(entry->!seen.add(entry.getResource().getIntraHash()));
		
		// present in B and in F, updates based on file entry
		List<Post<BibTex>> intersection = getPaperIntersection(fileEntries, accountEntries);
		for(Post<BibTex> post:intersection) {
			updateEntry(post);
			log.info(post.getResource().getTitle() + " updated");
		}
		
		// present in B, not in F, is removed
		List<Post<BibTex>> removeEntries = getExclusive(accountEntries, intersection);
		deleteEntries(removeEntries);
		
		//present in F, not in B, is added
		List<Post<BibTex>> addEntries = getExclusive(fileEntries, intersection);
		for (Post<BibTex> post : addEntries) {
			uploadEntry(post);
			log.info(post.getResource().getTitle() + " uploaded");
		}
	}
	
	public List<Post<BibTex>> loadAllEntriesFromAccount() throws Exception {
		int max = 0;
		final int max_entries = 1000;
		
		// it can retrieve only 1000 posts at a time
		List<Post<BibTex>> publications = logic.getPosts(BibTex.class, GroupingEntity.USER, username, null, null, null, null, null, Order.ADDED, null, null, max, max+max_entries);
		while(publications.size()==max+max_entries) {
			max += max_entries;
			List<Post<BibTex>> posts = logic.getPosts(BibTex.class, GroupingEntity.USER, username, null, null, null, null, null, Order.ADDED, null, null, max, max+max_entries);
			if(posts.isEmpty())
				break;
			publications.addAll(posts);
			
		}
		return publications;

	}
	private void updateEntry(Post<BibTex> entry) {
		entry.setUser(logic.getAuthenticatedUser());
		List<Post<? extends Resource>> post = Collections.<Post<? extends Resource>>singletonList(entry);
		logic.updatePosts(post, PostUpdateOperation.UPDATE_ALL);
		
	}
	
	public void deleteEntries(List<Post<BibTex>> posts) {
		logic.deletePosts(username, posts.stream().map(p -> p.getResource().getIntraHash()).collect(Collectors.toList()));
	}
	
	private List<Post<BibTex>> getPaperIntersection(List<Post<BibTex>> lista, List<Post<BibTex>> listb) {
		 return lista.stream()
		        .filter(f -> listb.stream().anyMatch(b -> b.getResource().getIntraHash().equals(f.getResource().getIntraHash())))
		        .collect(Collectors.toList());
	}
	
	private List<Post<BibTex>> getExclusive(List<Post<BibTex>> list, List<Post<BibTex>> intersection) {
		 return list.stream()
				.filter(b -> intersection.stream().noneMatch(i -> i.getResource().getIntraHash().equals(b.getResource().getIntraHash())))
				.collect(Collectors.toList());
	}

}
