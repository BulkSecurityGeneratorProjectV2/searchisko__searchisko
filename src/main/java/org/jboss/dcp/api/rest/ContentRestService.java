/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.dcp.api.rest;

import java.util.Date;
import java.util.Map;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.search.sort.SortOrder;
import org.jboss.dcp.api.annotations.security.GuestAllowed;
import org.jboss.dcp.api.annotations.security.ProviderAllowed;
import org.jboss.dcp.api.service.ProviderService;

/**
 * REST API for Content
 * 
 * @author Libor Krzyzanek
 * 
 */
@RequestScoped
@Path("/content/{type}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ProviderAllowed
public class ContentRestService extends RestServiceBase {

	/** DCP normalized document field constant */
	public static final String DCP_UPDATED = "dcp_updated";
	/** DCP normalized document field constant */
	public static final String DCP_TYPE = "dcp_type";
	/** DCP normalized document field constant */
	public static final String DCP_ID = "dcp_id";
	/** DCP normalized document field constant */
	public static final String DCP_CONTENT_ID = "dcp_content_id";
	/** DCP normalized document field constant */
	public static final String DCP_CONTENT_TYPE = "dcp_content_type";
	/** DCP normalized document field constant */
	public static final String DCP_CONTENT_PROVIDER = "dcp_content_provider";
	/** DCP normalized document field constant */
	public static final String DCP_TAGS = "dcp_tags";

	@Inject
	protected ProviderService providerService;

	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	@GuestAllowed
	public Object getAllContent(@PathParam("type") String type, @QueryParam("from") Integer from,
			@QueryParam("size") Integer size, @QueryParam("sort") String sort) {
		if (type == null || type.length() == 0) {
			return createRequiredFieldResponse("type");
		}
		try {
			Map<String, Object> typeDef = providerService.findContentType(type);
			if (typeDef == null) {
				return createBadFieldDataResponse("type");
			}

			String indexName = ProviderService.getIndexName(typeDef);
			String indexType = ProviderService.getIndexType(typeDef);
			checkSearchIndexSettings(type, indexName, indexType);

			SearchRequestBuilder srb = new SearchRequestBuilder(getSearchClientService().getClient());
			srb.setIndices(indexName);
			srb.setTypes(indexType);

			srb.setQuery(QueryBuilders.matchAllQuery());

			if (from != null) {
				srb.setFrom(from);
			}
			if (size != null) {
				srb.setSize(size);
			}
			if (sort != null) {
				if (sort.equalsIgnoreCase(SortOrder.ASC.name())) {
					srb.addSort(DCP_UPDATED, SortOrder.ASC);
				} else if (sort.equalsIgnoreCase(SortOrder.DESC.name())) {
					srb.addSort(DCP_UPDATED, SortOrder.DESC);
				}
			}

			final SearchResponse response = srb.execute().actionGet();

			return new ESDataOnlyResponse(response);
		} catch (IndexMissingException e) {
			return Response.status(Response.Status.NOT_FOUND).build();
		} catch (Exception e) {
			return createErrorResponse(e);
		}
	}

	@GET
	@Path("/{contentId}")
	@Produces(MediaType.APPLICATION_JSON)
	@GuestAllowed
	public Object getContent(@PathParam("type") String type, @PathParam("contentId") String contentId) {

		// validation
		if (contentId == null || contentId.length() == 0) {
			return createRequiredFieldResponse("contentId");
		}
		if (type == null || type.length() == 0) {
			return createRequiredFieldResponse("type");
		}
		try {
			Map<String, Object> typeDef = providerService.findContentType(type);
			if (typeDef == null) {
				return createBadFieldDataResponse("type");
			}

			String indexName = ProviderService.getIndexName(typeDef);
			String indexType = ProviderService.getIndexType(typeDef);
			checkSearchIndexSettings(type, indexName, indexType);

			GetResponse getResponse = getSearchClientService().getClient().prepareGet(indexName, indexType, contentId)
					.execute().actionGet();

			if (!getResponse.exists()) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}

			return createResponse(getResponse);
		} catch (IndexMissingException e) {
			return Response.status(Response.Status.NOT_FOUND).build();
		} catch (Exception e) {
			return createErrorResponse(e);
		}
	}

	/**
	 * Check if search index settings are correct.
	 * 
	 * @param type we check settings for (used for error message)
	 * @param indexName index name defined for given type
	 * @param indexType index type for given type
	 * @throws SettingsException if index name or type is null or empty
	 */
	protected static void checkSearchIndexSettings(String type, String indexName, String indexType) {
		if (indexName == null || indexName.trim().isEmpty() || indexType == null || indexType.trim().isEmpty()) {
			throw new SettingsException("Search index or type is not defined correctly for dcp_provider_type=" + type
					+ ". Contact administrators please.");
		}
	}

	@POST
	@Path("/{contentId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@ProviderAllowed
	public Object pushContent(@PathParam("type") String type, @PathParam("contentId") String contentId,
			Map<String, Object> content) {

		// validation
		if (contentId == null || contentId.isEmpty()) {
			return createRequiredFieldResponse("contentId");
		}
		if (type == null || type.isEmpty()) {
			return createRequiredFieldResponse("type");
		}
		if (content == null || content.isEmpty()) {
			return Response.status(Status.BAD_REQUEST).entity("Some content for pushing must be defined").build();
		}
		try {
			Map<String, Object> provider = providerService.findProvider(getProvider());
			Map<String, Object> typeDef = ProviderService.getContentType(provider, type);
			if (typeDef == null) {
				return createBadFieldDataResponse("type");
			}

			// check search subsystem configuration
			String indexName = ProviderService.getIndexName(typeDef);
			String indexType = ProviderService.getIndexType(typeDef);
			checkSearchIndexSettings(type, indexName, indexType);

			// Run preprocessors
			providerService.runPreprocessors(ProviderService.getPreprocessors(typeDef), content);

			// Copy distinct data from content to normalized fields
			content.put(DCP_TAGS, content.get("tags"));
			// TODO EXTERNAL_TAGS - add external tags for this document into dcp_tags field

			// fill some normalized fields - should be last step to avoid changing them via preprocessors
			content.put(DCP_CONTENT_PROVIDER, getProvider());
			content.put(DCP_CONTENT_ID, contentId);
			content.put(DCP_CONTENT_TYPE, type);
			content.put(DCP_ID, providerService.generateDcpId(type, contentId));
			content.put(DCP_TYPE, ProviderService.getDcpType(typeDef, type));
			if (content.get(DCP_UPDATED) == null) {
				content.put(DCP_UPDATED, new Date());
			}

			// TODO PERSISTENCE - Store to Persistance

			// Push to search subsystem
			getSearchClientService().getClient().prepareIndex(indexName, indexType, contentId).setSource(content).execute()
					.actionGet();

			return Response.ok("Content was successfully pushed").build();
		} catch (Exception e) {
			return createErrorResponse(e);
		}

	}

	@DELETE
	@Path("/{contentId}")
	@ProviderAllowed
	public Object deleteContent(@PathParam("type") String type, @PathParam("contentId") String contentId) {

		// validation
		if (contentId == null) {
			return createRequiredFieldResponse("contentId");
		}
		if (type == null) {
			return createRequiredFieldResponse("type");
		}
		try {
			Map<String, Object> provider = providerService.findProvider(getProvider());
			Map<String, Object> typeDef = ProviderService.getContentType(provider, type);
			if (typeDef == null) {
				return createBadFieldDataResponse("type");
			}

			// TODO PERSISTENCE - Remove from persistance if exists

			String indexName = ProviderService.getIndexName(typeDef);
			String indexType = ProviderService.getIndexType(typeDef);
			checkSearchIndexSettings(type, indexName, indexType);

			getSearchClientService().getClient().prepareDelete(indexName, indexType, contentId).execute().actionGet();

			return Response.ok().build();
		} catch (Exception e) {
			return createErrorResponse(e);
		}
	}
}
