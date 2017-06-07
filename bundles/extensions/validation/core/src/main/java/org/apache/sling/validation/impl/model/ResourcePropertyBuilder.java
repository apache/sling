/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.validation.impl.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.sling.validation.model.ValidatorInvocation;
import org.apache.sling.validation.model.ResourceProperty;

public class ResourcePropertyBuilder {

    private boolean optional;
    private boolean multiple;
    private String nameRegex;
    private final @Nonnull List<ValidatorInvocation> validators;

    public ResourcePropertyBuilder() {
        validators = new ArrayList<ValidatorInvocation>();
        this.nameRegex = null;
        this.optional = false;
        this.multiple = false;
    }

    public @Nonnull ResourcePropertyBuilder nameRegex(String nameRegex) {
        this.nameRegex = nameRegex;
        return this;
    }

    /** 
     * should only be used from test classes 
     */
    public @Nonnull ResourcePropertyBuilder validator(@Nonnull String id, Integer severity, String... parametersNamesAndValues) {
        if (parametersNamesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("array parametersNamesAndValues must be even! (first specify name then value, separated by comma)");
        }
        // convert to map
        Map<String, Object> parameterMap = new HashMap<String, Object>();
        for (int i=0; i<parametersNamesAndValues.length; i=i+2) {
            parameterMap.put(parametersNamesAndValues[i], parametersNamesAndValues[i+1]);
        }
        return validator(id, severity, parameterMap);
    }
    
    public @Nonnull ResourcePropertyBuilder validator(@Nonnull String id, Integer severity, @Nonnull Map<String, Object> parameters) {
        validators.add(new ValidatorInvocationImpl(id, parameters, severity));
        return this;
    }

    public @Nonnull ResourcePropertyBuilder optional() {
        this.optional = true;
        return this;
    }

    public @Nonnull ResourcePropertyBuilder multiple() {
        this.multiple = true;
        return this;
    }

    public @Nonnull ResourceProperty build(@Nonnull String name) {
        return new ResourcePropertyImpl(name, nameRegex, multiple, !optional, validators);
    }

    public @Nonnull ResourcePropertyBuilder name(@Nonnull String name) {
        this.name = name;
        return this;
    }

    public @Nonnull ResourceProperty build() {
        return new ResourcePropertyImpl(name, nameRegex, multiple, !optional, validators);
    }

    public ResourcePropertyBuilder addFieldValidator(FieldValidator fieldValidator) {
        Map<String, Object> validatorArguments = Arrays.asList(fieldValidator.validatorArguments())
                .parallelStream()
                .map(str -> str.split("="))
                .collect(Collectors.toMap(keyvalue -> keyvalue[0], keyvalue ->keyvalue[1]));

        return validator(fieldValidator.validatorName(), fieldValidator.severity(), validatorArguments);
    }

    public ResourcePropertyBuilder addValueMapValue(DefaultInjectionStrategy defaultInjectionStrategy, Field field) {

        ValueMapValue valueMapValue = field.getAnnotation(ValueMapValue.class);
        InjectionStrategy injectionStrategy = valueMapValue.injectionStrategy();
        if (injectionStrategy.equals(InjectionStrategy.OPTIONAL) || (injectionStrategy.equals(InjectionStrategy.DEFAULT)
                && defaultInjectionStrategy.equals(DefaultInjectionStrategy.OPTIONAL))) {
            this.optional();
        }

        if (field.getType().isArray() || Collection.class.isAssignableFrom(field.getType())) {
            this.multiple();
        }

        String fieldName = valueMapValue.name();
        if (fieldName.isEmpty()) {
            fieldName = field.getName();
        }

        return name(fieldName);
    }

    public ResourcePropertyBuilder addNameRegex(NameRegex nameRegex) {
        return nameRegex(nameRegex.regex());
    }
}
