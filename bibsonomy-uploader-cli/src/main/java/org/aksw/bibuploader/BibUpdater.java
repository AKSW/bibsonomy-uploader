package org.aksw.bibuploader;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bibsonomy.bibtex.parser.PostBibTeXParser;
import org.bibsonomy.common.enums.GroupingEntity;
import org.bibsonomy.model.BibTex;
import org.bibsonomy.model.Post;
import org.bibsonomy.model.Resource;
import org.bibsonomy.model.Tag;
import org.bibsonomy.model.User;
import org.bibsonomy.rest.client.Bibsonomy;
import org.bibsonomy.rest.client.exception.ErrorPerformingRequestException;
import org.bibsonomy.rest.client.queries.delete.DeletePostQuery;
import org.bibsonomy.rest.client.queries.get.GetPostsQuery;
import org.bibsonomy.rest.client.queries.post.CreatePostQuery;

public class BibUpdater {

	private Bibsonomy bibClient;

	private String username;

	private String fileLocation;

	private static Log log = LogFactory.getLog(BibUpdater.class);

	public BibUpdater(String username, String apikey, String apiurl,
			String fileLocation) {

		log.debug("Creating a new BibUpdater.");
		this.username = username;
		this.fileLocation = fileLocation;

		bibClient = new Bibsonomy();
		bibClient.setUsername(username);
		bibClient.setApiKey(apikey);
		bibClient.setApiURL(apiurl);

	}

	public static void main(String[] args) {

		if (args.length != 4) {

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
		DeletePostQuery del = new DeletePostQuery(username, post
				.getResource().getIntraHash());
		bibClient.executeQuery(del);
	}

	private void flushNpush() throws Exception {
		// get all previously posted entries
		List<Post<BibTex>> accountEntries = loadEntriesFromAccount();

		// and delete them
		for (Post<BibTex> post : accountEntries) {
			deleteEntry(post);
			log.info(post.getResource().getTitle() + " deleted");
		}

		// load entries
		List<Post<BibTex>> fileEntries = loadEntriesFromFile();

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

		GetPostsQuery postsQuery = new GetPostsQuery(0, 1000);
		
		
		postsQuery.setResourceType(BibTex.class);

		postsQuery.setGrouping(GroupingEntity.USER, username);

		bibClient.executeQuery(postsQuery);

		List<Post<BibTex>> remoteList = new ArrayList<Post<BibTex>>();

		for (Post<? extends Resource> post : postsQuery.getResult()) {

			remoteList.add((Post<BibTex>) post);
		}
		return remoteList;

	}

	public void uploadEntry(Post<BibTex> entry) throws Exception {

		entry.setUser(new User(this.username));
		
		if(entry.getTags()==null||entry.getTags().isEmpty()){
			entry.addTag("nokeyword");
			log.warn("Please add keywords for entry: " +  entry.getResource().getTitle());
		}

		CreatePostQuery upload = new CreatePostQuery(this.username, entry);

		this.bibClient.executeQuery(upload);

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
