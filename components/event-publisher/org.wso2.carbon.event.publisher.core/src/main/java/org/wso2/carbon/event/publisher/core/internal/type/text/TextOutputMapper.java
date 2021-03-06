/*
 * Copyright (c) 2005 - 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.wso2.carbon.event.publisher.core.internal.type.text;

import org.wso2.carbon.databridge.commons.Attribute;
import org.wso2.carbon.databridge.commons.StreamDefinition;
import org.wso2.carbon.event.publisher.core.config.EventPublisherConfiguration;
import org.wso2.carbon.event.publisher.core.config.EventPublisherConstants;
import org.wso2.carbon.event.publisher.core.config.mapping.TextOutputMapping;
import org.wso2.carbon.event.publisher.core.exception.EventPublisherConfigurationException;
import org.wso2.carbon.event.publisher.core.exception.EventPublisherStreamValidationException;
import org.wso2.carbon.event.publisher.core.internal.OutputMapper;
import org.wso2.carbon.event.publisher.core.internal.ds.EventPublisherServiceValueHolder;
import org.wso2.siddhi.core.event.Event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TextOutputMapper implements OutputMapper {

    private List<String> mappingTextList;
    private EventPublisherConfiguration eventPublisherConfiguration = null;
    private Map<String, Integer> propertyPositionMap = null;
    private final StreamDefinition streamDefinition;

    public TextOutputMapper(EventPublisherConfiguration eventPublisherConfiguration,
                            Map<String, Integer> propertyPositionMap, int tenantId,
                            StreamDefinition streamDefinition) throws
                                                               EventPublisherConfigurationException {
        this.eventPublisherConfiguration = eventPublisherConfiguration;
        this.propertyPositionMap = propertyPositionMap;
        this.streamDefinition = streamDefinition;

        if (eventPublisherConfiguration.getOutputMapping().isCustomMappingEnabled()) {
            validateStreamDefinitionWithOutputProperties(tenantId);
        } else {
            generateTemplateTextEvent(streamDefinition);
        }

    }

    private List<String> getOutputMappingPropertyList(String mappingText) {

        List<String> mappingTextList = new ArrayList<String>();
        String text = mappingText;

        mappingTextList.clear();
        while (text.contains(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX) && text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX) > 0) {
            mappingTextList.add(text.substring(text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX) + 2, text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX)));
            text = text.substring(text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX) + 2);
        }
        return mappingTextList;
    }

    private void setMappingTextList(String mappingText) {

        List<String> mappingTextList = new ArrayList<String>();
        String text = mappingText;

        mappingTextList.clear();
        while (text.contains(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX) && text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX) > 0) {
            mappingTextList.add(text.substring(0, text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX)));
            mappingTextList.add(text.substring(text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX) + 2, text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX)));
            text = text.substring(text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX) + 2);
        }
        mappingTextList.add(text);
        this.mappingTextList = mappingTextList;
    }

    private void validateStreamDefinitionWithOutputProperties(int tenantId)
            throws EventPublisherConfigurationException {

        TextOutputMapping textOutputMapping = ((TextOutputMapping) eventPublisherConfiguration.getOutputMapping());
        String actualMappingText = textOutputMapping.getMappingText();
        if (textOutputMapping.isRegistryResource()) {
            actualMappingText = EventPublisherServiceValueHolder.getCarbonEventPublisherService().getRegistryResourceContent(textOutputMapping.getMappingText());
        }

        setMappingTextList(actualMappingText);
        List<String> mappingProperties = getOutputMappingPropertyList(actualMappingText);

        Iterator<String> mappingTextListIterator = mappingProperties.iterator();
        for (; mappingTextListIterator.hasNext(); ) {
            String property = mappingTextListIterator.next();
            if (!propertyPositionMap.containsKey(property)) {
                throw new EventPublisherStreamValidationException("Property '" + property + "' is not in the input stream definition. ", streamDefinition.getStreamId());
            }
        }

    }

    @Override
    public Object convertToMappedInputEvent(Event event)
            throws EventPublisherConfigurationException {
        StringBuilder eventText = new StringBuilder(mappingTextList.get(0));
        for (int i = 1; i < mappingTextList.size(); i++) {
            if (i % 2 == 0) {
                eventText.append(mappingTextList.get(i));
            } else {
                eventText.append(getPropertyValue(event.getData(), mappingTextList.get(i)));
            }
        }
        return eventText.toString();
    }

    @Override
    public Object convertToTypedInputEvent(Event event)
            throws EventPublisherConfigurationException {
        return convertToMappedInputEvent(event);
    }


    private String getPropertyValue(Object[] eventData, String mappingProperty) {
        if (eventData.length != 0) {
            int position = propertyPositionMap.get(mappingProperty);
            Object data = eventData[position];
            if (data != null) {
                return data.toString();
            }
        }
        return "";
    }

    private void generateTemplateTextEvent(StreamDefinition streamDefinition) {

        String templateTextEvent = "";

        List<Attribute> metaDatAttributes = streamDefinition.getMetaData();
        if (metaDatAttributes != null && metaDatAttributes.size() > 0) {
            for (Attribute attribute : metaDatAttributes) {
                templateTextEvent += "\n" + EventPublisherConstants.PROPERTY_META_PREFIX + attribute.getName() + EventPublisherConstants.EVENT_ATTRIBUTE_VALUE_SEPARATOR + EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX + EventPublisherConstants.PROPERTY_META_PREFIX + attribute.getName() + EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX + EventPublisherConstants.EVENT_ATTRIBUTE_SEPARATOR;
            }
        }

        List<Attribute> correlationAttributes = streamDefinition.getCorrelationData();
        if (correlationAttributes != null && correlationAttributes.size() > 0) {
            for (Attribute attribute : correlationAttributes) {
                templateTextEvent += "\n" + EventPublisherConstants.PROPERTY_CORRELATION_PREFIX + attribute.getName() + EventPublisherConstants.EVENT_ATTRIBUTE_VALUE_SEPARATOR + EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX + EventPublisherConstants.PROPERTY_CORRELATION_PREFIX + attribute.getName() + EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX + EventPublisherConstants.EVENT_ATTRIBUTE_SEPARATOR;
            }
        }


        List<Attribute> payloadAttributes = streamDefinition.getPayloadData();
        if (payloadAttributes != null && payloadAttributes.size() > 0) {
            for (Attribute attribute : payloadAttributes) {
                templateTextEvent += "\n" + attribute.getName() + EventPublisherConstants.EVENT_ATTRIBUTE_VALUE_SEPARATOR + EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX + attribute.getName() + EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX + EventPublisherConstants.EVENT_ATTRIBUTE_SEPARATOR;
            }
        }
        if (templateTextEvent.trim().endsWith(EventPublisherConstants.EVENT_ATTRIBUTE_SEPARATOR)) {
            setMappingTextList(templateTextEvent.substring(0, templateTextEvent.length() - 1).trim());
        } else {
            setMappingTextList(templateTextEvent);
        }
    }

}
