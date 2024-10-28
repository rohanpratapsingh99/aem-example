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
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	        "sling.servlet.paths=/bin/loginuser"    }
	)
public class LoginApiServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(LoginApiServlet.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;
    @Override
    protected void doPost(final SlingHttpServletRequest request,
            final SlingHttpServletResponse response) throws ServletException, IOException {
    	response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Parse JSON from the request body
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

        // Extract userId and password from JSON input
        String userId = jsonInput.has("username") ? jsonInput.get("username").getAsString() : null;
        String password = jsonInput.has("password") ? jsonInput.get("password").getAsString() : null;


        if (StringUtils.isBlank(userId)|| StringUtils.isBlank(password)) {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Missing userId or password\"}");
            return;
        }

        Map<String, Object> param = new HashMap<>();
        param.put(ResourceResolverFactory.SUBSERVICE, "dataWriteService");  // Service user mapping required for reading secure paths

        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(param)) {
            String userType = "";
            String userLevel = "";
            Resource userResource = null;

            // Check under /content/user/<user>
            userResource = resolver.getResource("/content/user/" + userId);
            if (userResource != null && passwordMatches(userResource, password)) {
                userType = "user";
                userLevel = "level1";
            }

            // Check under /content/admin/<admin> if not found in user
            if (userResource == null) {
                userResource = resolver.getResource("/content/admin/" + userId);
                if (userResource != null && passwordMatches(userResource, password)) {
                    userType = "admin";
                    userLevel = "level2";
                }
            }

            // If user is found
            if (Objects.nonNull(userResource) && !StringUtils.isBlank(userLevel)) {
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("userType", userType);
                jsonResponse.addProperty("userLevel", userLevel);
                jsonResponse.addProperty("username", userId);

                response.getWriter().write(jsonResponse.toString());
            } else {
                response.setStatus(SlingHttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid credentials\"}");
            }

        } catch (Exception e) {
            LOG.error("Error occurred while accessing user data", e);
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal server error\"}");
        }
    }
    private boolean passwordMatches(Resource userResource, String password) {
        // Placeholder for actual password matching logic, retrieve password property from userResource
        String storedPassword = userResource.getValueMap().get("password", String.class);
        return password.equals(storedPassword);
    }
}
