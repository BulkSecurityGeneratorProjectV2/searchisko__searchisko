/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.dcp.api.rest;

import java.util.HashMap;
import java.util.Map;

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

import org.jboss.dcp.api.service.EntityService;

/**
 * Base class for REST API for entity manipulation
 * 
 * @author Libor Krzyzanek
 * 
 */
public class RestEntityServiceBase extends RestServiceBase {

	protected EntityService entityService;

	public void setEntityService(EntityService entityService) {
		this.entityService = entityService;
	}

	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public Object getAll(@QueryParam("from") Integer from, @QueryParam("size") Integer size) {
		try {
			return entityService.getAll(from, size, null);
		} catch (Exception e) {
			return createErrorResponse(e);
		}
	}

	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Object get(@PathParam("id") String id) {
		try {
			Map<String, Object> ret = entityService.get(id);
			if (ret == null) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}
			return ret;
		} catch (Exception e) {
			return createErrorResponse(e);
		}
	}

	public Object getFiltered(String id, String[] fieldsToRemove) {
		try {
			Map<String, Object> ret = entityService.get(id);
			if (ret == null) {
				return Response.status(Response.Status.NOT_FOUND).build();
			} else {
				return ESDataOnlyResponse.removeFields(ret, fieldsToRemove);
			}
		} catch (Exception e) {
			return createErrorResponse(e);
		}
	}

	public Map<String, Object> createResponseWithId(String id) {
		Map<String, Object> result = new HashMap<String, Object>();
		result.put("id", id);
		return result;
	}

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Object create(Map<String, Object> data) {
		try {
			String id = entityService.create(data);
			return createResponseWithId(id);
		} catch (Exception e) {
			return createErrorResponse(e);
		}
	}

	@POST
	@Path("/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Object create(@PathParam("id") String id, Map<String, Object> data) {
		try {
			entityService.create(id, data);
			return createResponseWithId(id);
		} catch (Exception e) {
			return createErrorResponse(e);
		}
	}

	@DELETE
	@Path("/{id}")
	public Object delete(@PathParam("id") String id) {
		try {
			entityService.delete(id);
			return Response.ok().build();
		} catch (Exception e) {
			return createErrorResponse(e);
		}
	}

}
