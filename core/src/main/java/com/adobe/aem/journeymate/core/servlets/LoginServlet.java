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

import com.google.gson.JsonObject;
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

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.methods=POST",
        "sling.servlet.paths=/bin/login"
    }
)
public class LoginServlet extends SlingAllMethodsServlet {
    
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(LoginServlet.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String userId = request.getParameter("userId");
        String password = request.getParameter("password");

        if (userId == null || password == null) {
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
            if (userResource != null) {
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("userType", userType);
                jsonResponse.addProperty("userLevel", userLevel);
                jsonResponse.addProperty("userId", userId);

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