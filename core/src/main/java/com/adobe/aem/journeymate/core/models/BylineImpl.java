package com.adobe.aem.journeymate.core.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.models.factory.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.components.ComponentContext;
import com.adobe.aem.journeymate.core.models.Byline;
import com.adobe.cq.wcm.core.components.models.Image;

@Model(
        adaptables = {SlingHttpServletRequest.class},
        adapters = {Byline.class},
        resourceType = {BylineImpl.RESOURCE_TYPE},
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class BylineImpl implements Byline {
    protected static final String RESOURCE_TYPE = "journeymate/components/byline";

    @Self
    private SlingHttpServletRequest request;

    @OSGiService
    private ModelFactory modelFactory;

    @ScriptVariable
    private Page currentPage;

    @ScriptVariable
    protected ComponentContext componentContext;

    @ValueMapValue
    private String name;

    @ValueMapValue
    private List<String> occupations;

    private Image image;

    // Add a logger for any errors
    private static final Logger LOGGER = LoggerFactory.getLogger(BylineImpl.class);

    @PostConstruct
    private void init() {
        image = modelFactory.getModelFromWrappedRequest(request, request.getResource(), Image.class);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getOccupations() {
         if (occupations != null) {
             Collections.sort(occupations);
             return new ArrayList<String>(occupations);
         } else {
             return Collections.emptyList();
         }
    }

    @Override
    public boolean isEmpty() {
        final Image image = getImage();

        if (StringUtils.isBlank(name)) {
            // Name is missing, but required
            return true;
        } else if (occupations == null || occupations.isEmpty()) {
            // At least one occupation is required
            return true;
        } else if (image == null || StringUtils.isBlank(image.getSrc())) {
            // A valid image is required
            return true;
        } else {
            // Everything is populated, so this component is not considered empty
            return false;
        }
    }

    /**
     * @return the Image Sling Model of this resource, or null if the resource cannot create a valid Image Sling Model. 
     */
    private Image getImage() {
        return image;
    }
}