package org.aksw.bibuploader;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bibsonomy.model.BibTex;
import org.bibsonomy.model.Post;
import org.bibsonomy.model.Resource;
import org.junit.Test;

public class BibUpdaterTest {
	private static Log log = LogFactory.getLog(BibUpdaterTest.class);

	@Test
	public void testMain() {
		fail("Not yet implemented");
	}

	@Test
	public void testParseFile() throws Exception {
		BibUpdater bibu = new BibUpdater("asdf", "asdf", "asdf", "./src/test/resources/aksw.bib");
		List<Post<BibTex>> list = bibu.loadEntriesFromFile();
		log.info("*************From File: ");
		for (Post<BibTex> post : list) {
			log.info( post.getResource().getTitle());
		}
		
		
		
		
	}
	
	
	@Test
	public void loadEntriesFromAccount() throws Exception{
		BibUpdater bibu = new BibUpdater("aksw", "enterAPIkeyHERE", "http://www.bibsonomy.org/api", "nothere");
				
		log.info("***************\n\nFrom Account:");
		int i =0;
		List<Post<BibTex>> listacc = bibu.loadEntriesFromAccount();
		for (Post<BibTex> post : listacc) {
			log.info(++i +". "+ post.getResource().getTitle() + "  " + post.getResource().getIntraHash());
			
		}
//		log.info("***************\n\nTrying to Delete:");
//		for (Post<BibTex> post : listacc) {
//			bibu.deleteEntry(post);
//		}
	}
	
	
	
}
