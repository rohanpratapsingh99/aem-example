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
import java.util.Objects;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component(service = Servlet.class, property = { "sling.servlet.methods=POST",
		"sling.servlet.paths=/bin/registeruser" })
public class RegisterServlet extends SlingAllMethodsServlet {

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
            Resource adminRoot = resolver.getResource("/content/admin");
            if (userRoot == null || adminRoot == null) {
                response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"User or Admin storage location does not exist\"}");
                return;
            }

            // Check if the username or email already exists in user or admin nodes
            if (resourceExists(resolver, "/content/user/" + username) || 
                resourceExists(resolver, "/content/admin/" + username) || 
                emailExists(resolver, email, userRoot) || 
                emailExists(resolver, email, adminRoot)) {
                
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("error", "User with this username or email already exists");
                response.getWriter().write(jsonResponse.toString());
                return;
            }

            // Set properties for new user node
            Map<String, Object> properties = new HashMap<>();
            properties.put("jcr:primaryType", "nt:unstructured");
            properties.put("firstname", firstName);
            properties.put("lastname", lastName);
            properties.put("email", email);
            properties.put("password", password); // Consider hashing the password
            properties.put("mobile", mobile);

            // Create the new user node as nt:unstructured
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

    private boolean resourceExists(ResourceResolver resolver, String path) {
        return resolver.getResource(path) != null;
    }

    private boolean emailExists(ResourceResolver resolver, String email, Resource rootResource) {
        if (rootResource != null) {
            for (Resource user : rootResource.getChildren()) {
                String existingEmail = user.getValueMap().get("email", String.class);
                if (email.equals(existingEmail)) {
                    return true;
                }
            }
        }
        return false;
    }
}