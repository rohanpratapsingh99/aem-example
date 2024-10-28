/*
 *  Copyright 2015 Adobe Systems Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.adobe.aem.journeymate.core.servlets;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Servlet that writes some sample content into the response. It is mounted for
 * all resources of a specific Sling resource type. The
 * {@link SlingSafeMethodsServlet} shall be used for HTTP methods that are
 * idempotent. For write operations use the {@link SlingAllMethodsServlet}.
 */
@Component(
	    service = Servlet.class,
	    property = {
	        "sling.servlet.methods=POST",
	        "sling.servlet.paths=/bin/sample"    }
	)
public class SimpleServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(SimpleServlet.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;
    @Override
    protected void doPost(final SlingHttpServletRequest request,
            final SlingHttpServletResponse response) throws ServletException, IOException {
    	StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        } catch (IOException e) {
            LOG.error("Error reading request body", e);
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid request format\"}");
            return;
        }

        JsonObject jsonInput;
        try {
            jsonInput = JsonParser.parseString(requestBody.toString()).getAsJsonObject();
        } catch (Exception e) {
            LOG.error("Error parsing JSON", e);
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid JSON format\"}");
            return;
        }

        // Extract user details from JSON input
        String username = jsonInput.has("username") ? jsonInput.get("username").getAsString() : null;
        String email = jsonInput.has("email") ? jsonInput.get("email").getAsString() : null;
        String firstName = jsonInput.has("firstname") ? jsonInput.get("firstname").getAsString() : null;
        String lastName = jsonInput.has("lastname") ? jsonInput.get("lastname").getAsString() : null;
        String password = jsonInput.has("password") ? jsonInput.get("password").getAsString() : null;
        String mobile = jsonInput.has("mobile") ? jsonInput.get("mobile").getAsString() : null;

        if (username == null || email == null || password == null) {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Missing required fields\"}");
            return;
        }

        Map<String, Object> param = new HashMap<>();
        param.put(ResourceResolverFactory.SUBSERVICE, "dataWriteService");

        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(param)) {
            Resource userRoot = resolver.getResource("/content/user");
            if (userRoot == null) {
                response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"User storage location does not exist\"}");
                return;
            }

            // Check if the username or email already exists
            Resource existingUser = resolver.getResource("/content/user/" + username);
            if (existingUser != null || emailExists(resolver, email)) {
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("error", "User with this username or email already exists");
                response.getWriter().write(jsonResponse.toString());
                return;
            }

            // Create a new user node with properties
            Map<String, Object> properties = new HashMap<>();
            properties.put("firstname", firstName);
            properties.put("lastname", lastName);
            properties.put("email", email);
            properties.put("password", password); // Consider storing hashed password for security
            properties.put("mobile", mobile);
            properties.put("username", username);
            properties.put("jcr:primaryType", "nt:unstructured");

            
            resolver.create(userRoot, username, properties);
            resolver.commit();

            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("message", "User registered successfully");
            response.getWriter().write(jsonResponse.toString());

        } catch (Exception e) {
            LOG.error("Error occurred during user registration", e);
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal server error\"}");
        }
    }

    private boolean emailExists(ResourceResolver resolver, String email) {
        // Query or check all user nodes to see if email exists
        Resource userRoot = resolver.getResource("/content/user");
        if (userRoot != null) {
            for (Resource user : userRoot.getChildren()) {
                String existingEmail = user.getValueMap().get("email", String.class);
                if (email.equals(existingEmail)) {
                    return true;
                }
            }
        }
        return false;
    }
}
