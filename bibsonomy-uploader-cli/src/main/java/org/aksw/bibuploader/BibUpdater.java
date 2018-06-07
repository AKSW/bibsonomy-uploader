package org.aksw.bibuploader;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bibsonomy.bibtex.parser.PostBibTeXParser;
import org.bibsonomy.common.enums.GroupingEntity;
import org.bibsonomy.model.BibTex;
import org.bibsonomy.model.Post;
import org.bibsonomy.model.Resource;
import org.bibsonomy.model.User;
import org.bibsonomy.model.enums.Order;
import org.bibsonomy.model.logic.LogicInterface;
import org.bibsonomy.rest.client.RestLogicFactory;
import org.bibsonomy.rest.client.queries.delete.DeletePostQuery;
import org.bibsonomy.rest.client.queries.get.GetPostsQuery;
import org.bibsonomy.rest.client.queries.post.CreatePostQuery;
import org.bibsonomy.rest.renderer.xml.BibsonomyXML;

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
				update.flushNpush();

			} catch (Exception e) {

				log.error("Error occured:", e);
			}

		}

	}
	
	public void deleteEntry(Post<BibTex> post) throws Exception{
//		DeletePostQuery del = new DeletePostQuery(username, post.getResource().getIntraHash());
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
				this.fileLocation));

		PostBibTeXParser parser = new PostBibTeXParser();

		List<Post<BibTex>> posts = parser.parseBibTeXPosts(bibtexString);

		for (Post<BibTex> post : posts) {
			post.getResource().recalculateHashes();
		}

		return posts;

	}

	public List<Post<BibTex>> loadEntriesFromAccount() throws Exception {

//		GetPostsQuery postsQuery = new GetPostsQuery(0, 2000);
//		
//		
//		postsQuery.setResourceType(BibTex.class);
//
//		postsQuery.setGrouping(GroupingEntity.USER, username);
//
//		bibClient.executeQuery(postsQuery);
//
//		List<Post<BibTex>> remoteList = new ArrayList<Post<BibTex>>();
//
//		for (Post<? extends Resource> post : postsQuery.getResult()) {
//
//			remoteList.add((Post<BibTex>) post);
//		}
		
		List<Post<BibTex>> publications = logic.getPosts(BibTex.class, GroupingEntity.USER, "aksw", null, null, null, null, null, Order.ADDED, null, null, 0, 1000);
		return publications;

	}

	public void uploadEntry(Post<BibTex> entry) {

//		entry.setUser(new User(this.username));
//		
//		if(entry.getTags()==null||entry.getTags().isEmpty()){
//			entry.addTag("nokeyword");
//			log.warn("Please add keywords for entry: " +  entry.getResource().getTitle());
//		}
//
//		CreatePostQuery upload = new CreatePostQuery(this.username, entry);
//		logix.executeQuery(upload);
		
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

}
