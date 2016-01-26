package org.aksw.sparqlcache4j;

import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.management.ManagementService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wicket.util.io.IOUtils;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.sparql.core.BasicPattern;
import com.hp.hpl.jena.sparql.engine.http.HttpParams;
import com.hp.hpl.jena.sparql.engine.http.HttpQuery;
import com.hp.hpl.jena.sparql.engine.http.Params;
import com.hp.hpl.jena.sparql.engine.http.QueryExceptionHTTP;
import com.hp.hpl.jena.sparql.modify.UpdateVisitor;
import com.hp.hpl.jena.sparql.modify.op.Update;
import com.hp.hpl.jena.sparql.modify.op.UpdateClear;
import com.hp.hpl.jena.sparql.modify.op.UpdateCreate;
import com.hp.hpl.jena.sparql.modify.op.UpdateDelete;
import com.hp.hpl.jena.sparql.modify.op.UpdateDeleteData;
import com.hp.hpl.jena.sparql.modify.op.UpdateDrop;
import com.hp.hpl.jena.sparql.modify.op.UpdateExt;
import com.hp.hpl.jena.sparql.modify.op.UpdateInsert;
import com.hp.hpl.jena.sparql.modify.op.UpdateInsertData;
import com.hp.hpl.jena.sparql.modify.op.UpdateLoad;
import com.hp.hpl.jena.sparql.modify.op.UpdateModify;
import com.hp.hpl.jena.sparql.syntax.ElementAssign;
import com.hp.hpl.jena.sparql.syntax.ElementDataset;
import com.hp.hpl.jena.sparql.syntax.ElementExists;
import com.hp.hpl.jena.sparql.syntax.ElementFetch;
import com.hp.hpl.jena.sparql.syntax.ElementFilter;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementNamedGraph;
import com.hp.hpl.jena.sparql.syntax.ElementNotExists;
import com.hp.hpl.jena.sparql.syntax.ElementOptional;
import com.hp.hpl.jena.sparql.syntax.ElementPathBlock;
import com.hp.hpl.jena.sparql.syntax.ElementService;
import com.hp.hpl.jena.sparql.syntax.ElementSubQuery;
import com.hp.hpl.jena.sparql.syntax.ElementTriplesBlock;
import com.hp.hpl.jena.sparql.syntax.ElementUnion;
import com.hp.hpl.jena.sparql.syntax.ElementVisitor;
import com.hp.hpl.jena.sparql.syntax.ElementWalker;
import com.hp.hpl.jena.sparql.syntax.Template;
import com.hp.hpl.jena.sparql.syntax.TemplateGroup;
import com.hp.hpl.jena.sparql.syntax.TemplateTriple;
import com.hp.hpl.jena.sparql.syntax.TemplateVisitor;
import com.hp.hpl.jena.update.UpdateRequest;
import com.sun.org.apache.bcel.internal.generic.NEW;

public class SparqlCache {
	{
		cacheManager = CacheManager.create();
		MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
		ManagementService.registerMBeans(cacheManager, mBeanServer, false,
				false, false, true);

	}
	
	private static int hitcount =0;

	private Log log = LogFactory.getLog(SparqlCache.class);

	private String remoteEndpoint = "http://localhost:8890/sparql";

	private String httpUser = null;

	private char[] httpPassword = null;

	//
	static CacheManager cacheManager;
	static net.sf.ehcache.management.CacheManager mbeancachemanager;

	public Cache getInstance(URL endpoint) {

		Cache cache = cacheManager.getCache(endpoint.toString().replace(":",
				"_"));
		if (cache == null) {

			synchronized (cacheManager) {
				cache = cacheManager.getCache(endpoint.toString().replace(":",
						"_"));
				if (cache == null) {

					cache = new Cache(endpoint.toString().replace(":", "_"),
							5000, false, false, 500, 200);

					cacheManager.addCache(cache);

					Element element = new Element(QueryTripleMapper.key,
							new QueryTripleMapper());
					element.setEternal(true);
					cache.put(element);
				}
			}
		}

		return cache;
	}

	public List<URL> getCachedEndpoints() {
		List<URL> endpoints = new ArrayList<URL>();

		String[] endpointString = cacheManager.getCacheNames();

		for (String string : endpointString) {
			try {
				endpoints.add(new URL(string));
			} catch (MalformedURLException e) {
				log.warn("Non URL cache: ", e);
			}
		}

		return endpoints;
	}

	public boolean isCached(URL endpointUri, Query query) {
		Cache cache = getInstance(endpointUri);
		return cache.isKeyInCache(query);
	}

	public String getCached(URL endpointUri, Query query) {
		Cache cache = getInstance(endpointUri);
		Element hit = cache.get(query.toString());

		String result = null;
		if (hit != null) {
			result = (String) hit.getObjectValue();
//			log.info(">>>>>>>>>>>\n Cache Hit: \n" + ">>>Key: \n"
//					+ query.toString());
			log.info("Cache Hit No ." + hitcount++);
			
		}

		return result;
	}

	public void put(URL endpointUri, Query query, String result) {
		Cache cache = getInstance(endpointUri);
		QueryTripleMapper mapper = (QueryTripleMapper) cache.get(
				QueryTripleMapper.key).getObjectValue();
		mapper.register(query);
		cache.put(new Element(query.toString(), result));
	}

	public void remove(URL endpointUri, Query query) {
		Cache cache = getInstance(endpointUri);
		QueryTripleMapper mapper = (QueryTripleMapper) cache.get(
				QueryTripleMapper.key).getObjectValue();
		mapper.unregister(query);
		cache.remove(query.toString());
	}

	public String query(URL endpointUri, Query query,
			List<String> defaultGraphURIs, List<String> namedGraphURIs,
			Params params) throws QueryExceptionHTTP, IOException {
		if (log.isDebugEnabled()) {
			log.debug(">>>>>>> \n Select Query received: \n" + query.toString());
		}

		String result = getCached(endpointUri, query);
		if (result == null) {
			log.debug(">>>>>>>>\n Result not found, querying store");
			result = queryRemoteHttpSparqlStore(query, defaultGraphURIs,
					namedGraphURIs, params);
			// log.info(">>>>>>>>\n Receivec from store: " + result);
			put(endpointUri, query, result);

		} else {

		}

		return result;

	}

	public String query(URL endpointUri, Query query)
			throws QueryExceptionHTTP, IOException {

		return query(endpointUri, query, new ArrayList<String>(),
				new ArrayList<String>(), null);

	}

	/**
	 * taken from arq
	 * 
	 * @param query
	 * @param defaultGraphURIs
	 * @param namedGraphURIs
	 * @param params
	 * @throws IOException
	 * @throws QueryExceptionHTTP
	 */
	public String queryRemoteHttpSparqlStore(Query query,
			List<String> defaultGraphURIs, List<String> namedGraphURIs,
			Params params) throws QueryExceptionHTTP, IOException {
		String response = null;

		HttpQuery httpQuery = new HttpQuery(this.remoteEndpoint);
		httpQuery.addParam(HttpParams.pQuery, query.toString());

		for (Iterator<String> iter = defaultGraphURIs.iterator(); iter
				.hasNext();) {
			String dft = iter.next();
			httpQuery.addParam(HttpParams.pDefaultGraph, dft);
		}
		for (Iterator<String> iter = namedGraphURIs.iterator(); iter.hasNext();) {
			String name = iter.next();
			httpQuery.addParam(HttpParams.pNamedGraph, name);
		}

		if (params != null)
			httpQuery.merge(params);

		httpQuery.setBasicAuthentication(this.httpUser, this.httpPassword);
		if(!(query.getQueryType() == Query.QueryTypeSelect)){
			httpQuery.setAccept(HttpParams.contentTypeRDFXML);
		}
		

		response = IOUtils.toString(httpQuery.exec());

		return response;

	}

	/**
	 * taken from arq
	 * 
	 * @param query
	 * @param defaultGraphURIs
	 * @param namedGraphURIs
	 * @param params
	 * @throws IOException
	 * @throws QueryExceptionHTTP
	 */
	public String updateRemoteHttpSparqlStore(UpdateRequest update,
			List<String> defaultGraphURIs, List<String> namedGraphURIs,
			Params params) throws QueryExceptionHTTP, IOException {
		String response = null;

		HttpQuery httpQuery = new HttpQuery(this.remoteEndpoint);
		httpQuery.addParam(HttpParams.pQuery, update.toString());

		for (Iterator<String> iter = defaultGraphURIs.iterator(); iter
				.hasNext();) {
			String dft = iter.next();
			httpQuery.addParam(HttpParams.pDefaultGraph, dft);
		}
		for (Iterator<String> iter = namedGraphURIs.iterator(); iter.hasNext();) {
			String name = iter.next();
			httpQuery.addParam(HttpParams.pNamedGraph, name);
		}

		if (params != null)
			httpQuery.merge(params);

		httpQuery.setBasicAuthentication(this.httpUser, this.httpPassword);

		response = IOUtils.toString(httpQuery.exec());

		return response;

	}

	public String modify(URL url, UpdateRequest updateRequest,
			List<String> defaultGraphs, List<String> namedGraphs, Params params)
			throws QueryExceptionHTTP, IOException {
		
		long stopPoint = System.currentTimeMillis();

		final List<Triple> triples = new ArrayList<Triple>();

		visitModify(updateRequest, triples);
		
		log.info("Visiting the query took: " + (System.currentTimeMillis() - stopPoint));
		stopPoint = System.currentTimeMillis();

		// check, which queries to invalidate
		Cache cache = getInstance(url);
		QueryTripleMapper mapper = (QueryTripleMapper) cache.get(
				QueryTripleMapper.key).getObjectValue();

		List<Query> invalidQueries = new ArrayList<Query>();

		for (Triple triple : triples) {
			invalidQueries.addAll(mapper.findInvalidQueries(triple));
		}
		
		log.info("Search for queries took: " + (System.currentTimeMillis() - stopPoint));
		stopPoint = System.currentTimeMillis();

		// pass the query on to the server
		String result = updateRemoteHttpSparqlStore(updateRequest,
				defaultGraphs, namedGraphs, params);

		// remove from cache
		
		log.info("Passing the query took: " + (System.currentTimeMillis() - stopPoint));
		stopPoint = System.currentTimeMillis();
		int cacheCountPrev = cache.getSize();

		for (Query query : invalidQueries) {
			remove(url, query);
			log.debug("removed query: " + query.toString());
		}
		
		log.info("Removing the queris took: " + (System.currentTimeMillis() - stopPoint));
		stopPoint = System.currentTimeMillis();
		
		log.info("Removed + " +invalidQueries.size()+ " queries, cache size is now: " + cache.getSize() +" compared to previously :" + cacheCountPrev );
		

		// return the servers response
		return result;
	}

	private void visitModify(UpdateRequest updateRequest,
			final List<Triple> triples) {
		// get all affected triples
		for (Update up : updateRequest.getUpdates()) {
			up.visit(new UpdateVisitor() {

				@Override
				public void visit(UpdateExt updateExt) {
					throw new RuntimeException(
							"Feature UpdateExt not yet implemented");

				}

				@Override
				public void visit(UpdateCreate create) {
					throw new RuntimeException(
							"Feature UpdateCreate not yet implemented");

				}

				@Override
				public void visit(UpdateDrop drop) {
					throw new RuntimeException(
							"Feature UpdateDrop not yet implemented");

				}

				@Override
				public void visit(UpdateLoad load) {
					throw new RuntimeException(
							"Feature UpdateLoad not yet implemented");

				}

				@Override
				public void visit(UpdateClear clear) {
					throw new RuntimeException(
							"Feature UpdateClear not yet implemented");

				}

				@Override
				public void visit(UpdateDeleteData remove) {
					throw new RuntimeException(
							"Feature UpdateDeleteData not yet implemented");

				}

				@Override
				public void visit(UpdateInsertData add) {
					throw new RuntimeException(
							"Feature UpdateInsertData not yet implemented");

				}

				@Override
				public void visit(UpdateInsert insert) {
					insert.getInsertTemplate().visit(new TemplateVisitor() {

						@Override
						public void visit(TemplateGroup templategroup) {
							// got a group

							for (Template template : templategroup
									.getTemplates()) {
								template.visit(this);
							}

						}

						@Override
						public void visit(TemplateTriple template) {
							triples.add(template.getTriple());
						}
					});
				}

				@Override
				public void visit(UpdateDelete delete) {
					throw new RuntimeException(
							"Feature UpdateDelete not yet implemented");

				}

				@Override
				public void visit(UpdateModify modify) {
					throw new RuntimeException(
							"Feature UpdateModify not yet implemented");

				}
			});

		}
	}

}
